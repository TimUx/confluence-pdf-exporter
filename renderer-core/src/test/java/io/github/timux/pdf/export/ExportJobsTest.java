package io.github.timux.pdf.export;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ExportJobsTest {
    @Test void completesOwnerScopedJobAndExpiresResults() throws Exception {
        class MutableClock extends Clock {
            long now;
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(now); }
        }
        MutableClock clock = new MutableClock();
        try (var jobs = new ExportJobs(clock)) {
            UUID id = jobs.submit("owner", List.of(1L), List.of(2L), "Title.pdf",
                    () -> new ExportJobs.Result(new byte[]{1, 2, 3}, List.of("Warning")));
            await(jobs, id);
            assertEquals(ExportJobs.State.COMPLETE, jobs.view(id, "owner").state());
            assertEquals(List.of(1L), jobs.pageIds(id, "owner"));
            assertEquals(List.of(2L), jobs.attachmentIds(id, "owner"));
            assertThrows(ExportException.class, () -> jobs.download(id, "other"));
            byte[] data = jobs.download(id, "owner"); data[0] = 0;
            assertEquals(1, jobs.download(id, "owner")[0]);
            clock.now = TimeUnit.MINUTES.toMillis(11);
            jobs.expire();
            assertThrows(ExportException.class, () -> jobs.view(id, "owner"));
        }
    }
    @Test void rejectsSecondActiveUserJobAndDoesNotExposeFailureDetails() throws Exception {
        var release = new CountDownLatch(1);
        try (var jobs = new ExportJobs()) {
            UUID id = jobs.submit("owner", List.of(1L), List.of(), "Title.pdf", () -> {
                release.await(5, TimeUnit.SECONDS);
                throw new IllegalStateException("Protected content");
            });
            assertThrows(ExportException.class, () -> jobs.submit("owner", List.of(), List.of(), "x", () -> null));
            release.countDown();
            await(jobs, id);
            assertEquals(ExportJobs.State.FAILED, jobs.view(id, "owner").state());
            assertFalse(jobs.view(id, "owner").error().contains("Protected"));
        } finally { release.countDown(); }
    }
    @Test void limitsWorkersAndQueueWithoutUnboundedTaskAdmission() {
        var release = new CountDownLatch(1);
        var started = new java.util.concurrent.atomic.AtomicInteger();
        try (var jobs = new ExportJobs()) {
            for (int i = 0; i < 4; i++) {
                jobs.submit("user-" + i, List.of(1L), List.of(), "Title.pdf", () -> {
                    started.incrementAndGet();
                    release.await(5, TimeUnit.SECONDS);
                    return new ExportJobs.Result(new byte[]{1}, List.of());
                });
            }
            assertThrows(ExportException.class, () -> jobs.submit("fifth-user", List.of(1L), List.of(),
                    "Title.pdf", () -> new ExportJobs.Result(new byte[]{1}, List.of())));
            assertTrue(started.get() <= 2);
        } finally { release.countDown(); }
    }
    private static void await(ExportJobs jobs, UUID id) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            var state = jobs.view(id, "owner").state();
            if (state == ExportJobs.State.COMPLETE || state == ExportJobs.State.FAILED) return;
            Thread.sleep(10);
        }
        fail("Job did not finish");
    }
}
