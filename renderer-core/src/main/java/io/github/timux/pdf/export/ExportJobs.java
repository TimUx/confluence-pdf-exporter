package io.github.timux.pdf.export;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Node-local, bounded, owner-scoped jobs. No host objects cross the worker boundary. */
public final class ExportJobs implements AutoCloseable {
    public enum State { QUEUED, RUNNING, COMPLETE, FAILED }
    public record Result(byte[] pdf, List<String> warnings) {
        public Result { pdf = pdf.clone(); warnings = List.copyOf(warnings); }
        @Override public byte[] pdf() { return pdf.clone(); }
    }
    public record View(UUID id, State state, String error, String fileName, List<String> warnings) {}
    private static final class Job {
        final UUID id = UUID.randomUUID();
        final String owner;
        final long created;
        final List<Long> pages;
        final List<Long> attachments;
        final String fileName;
        State state = State.QUEUED;
        Result result;
        String error = "";
        Job(String owner, long created, List<Long> pages, List<Long> attachments, String fileName) {
            this.owner = owner; this.created = created; this.pages = List.copyOf(pages);
            this.attachments = List.copyOf(attachments); this.fileName = fileName;
        }
    }
    private final Map<UUID, Job> jobs = new LinkedHashMap<>();
    private final ThreadPoolExecutor executor;
    private final Clock clock;
    private final java.util.concurrent.ScheduledExecutorService cleaner;
    private final Duration retention = Duration.ofMinutes(10);

    public ExportJobs() { this(Clock.systemUTC()); }
    public ExportJobs(Clock clock) {
        this.clock = clock;
        executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2),
                runnable -> daemon(runnable, "universal-pdf-worker"), new ThreadPoolExecutor.AbortPolicy());
        cleaner = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                runnable -> daemon(runnable, "universal-pdf-cleaner"));
        cleaner.scheduleAtFixedRate(this::expire, 1, 1, TimeUnit.MINUTES);
    }
    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name); thread.setDaemon(true); return thread;
    }

    public synchronized UUID submit(String owner, List<Long> pages, List<Long> attachments, String fileName,
                                    Callable<Result> work) {
        expire();
        if (jobs.size() >= 8) throw new ExportException("Auftragsspeicher voll. Bitte später erneut versuchen.");
        if (jobs.values().stream().anyMatch(j -> j.owner.equals(owner) &&
                (j.state == State.RUNNING || j.state == State.QUEUED)))
            throw new ExportException("Ein Export läuft bereits für diesen Benutzer.");
        Job job = new Job(owner, clock.millis(), pages, attachments, fileName);
        jobs.put(job.id, job);
        try {
            executor.execute(() -> {
                synchronized (this) { job.state = State.RUNNING; }
                try {
                    Result result = work.call();
                    synchronized (this) { job.result = result; job.state = State.COMPLETE; }
                } catch (Throwable ex) {
                    // Throwable, not Exception: a LinkageError in the renderer must not leave the job RUNNING forever.
                    synchronized (this) {
                        // Do not expose renderer exceptions, which may contain protected content or URLs.
                        // ExportException messages are written by this plugin and are safe to show.
                        job.error = ex instanceof ExportException ? ex.getMessage()
                                : "PDF-Erzeugung fehlgeschlagen oder Ressourcenlimit erreicht.";
                        job.state = State.FAILED;
                    }
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            jobs.remove(job.id);
            throw new ExportException("Exportwarteschlange voll.");
        }
        return job.id;
    }

    public synchronized View view(UUID id, String owner) {
        Job j = owned(id, owner);
        return new View(j.id, j.state, j.error, j.fileName, j.result == null ? List.of() : j.result.warnings());
    }
    public synchronized List<Long> pageIds(UUID id, String owner) { return owned(id, owner).pages; }
    public synchronized List<Long> attachmentIds(UUID id, String owner) { return owned(id, owner).attachments; }
    public synchronized byte[] download(UUID id, String owner) {
        Job j = owned(id, owner);
        if (j.state != State.COMPLETE) throw new ExportException("Export ist noch nicht verfügbar.");
        return j.result.pdf();
    }
    private Job owned(UUID id, String owner) {
        expire();
        Job j = jobs.get(id);
        if (j == null || !j.owner.equals(owner)) throw new ExportException("Auftrag nicht verfügbar.");
        return j;
    }
    public synchronized void expire() {
        long cutoff = clock.millis() - retention.toMillis();
        jobs.values().removeIf(j -> j.created < cutoff && j.state != State.RUNNING && j.state != State.QUEUED);
    }
    @Override public synchronized void close() {
        executor.shutdownNow(); cleaner.shutdownNow(); jobs.clear();
    }
}
