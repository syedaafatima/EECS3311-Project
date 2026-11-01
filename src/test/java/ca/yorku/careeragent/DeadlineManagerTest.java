package ca.yorku.careeragent;

import ca.yorku.careeragent.deadline.DeadlineManager;
import ca.yorku.careeragent.deadline.DeadlineObserver;
import ca.yorku.careeragent.deadline.DeadlineReminder;
import ca.yorku.careeragent.domain.Application;
import ca.yorku.careeragent.domain.Job;
import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.service.CareerFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeadlineManagerTest {
    @TempDir Path data;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T02:30:00Z"), ZoneId.of("America/Toronto"));
    private final LocalDate today = LocalDate.of(2026, 11, 1);

    private Job job(CareerFacade facade, String title, LocalDate deadline) {
        return facade.importJob(title, "Example", "Java skills", "", "Toronto", deadline);
    }

    @Test void listsOverdueTodayUpcomingAndUnknownInTorontoAndSuppressesFulfilledDeadlines() {
        Repository repo = new Repository(data);
        CareerFacade facade = new CareerFacade(repo);
        job(facade, "Today", today);
        job(facade, "Unknown", null);
        job(facade, "Past", today.minusDays(1));
        job(facade, "Future", today.plusDays(8));
        Job saved = job(facade, "Saved", today.plusDays(7));
        facade.trackApplication(saved.getId(), today, Application.Status.SAVED);
        for (Application.Status status : Application.Status.values()) {
            if (status == Application.Status.SAVED) continue;
            Job completed = job(facade, status.name(), today);
            facade.trackApplication(completed.getId(), today, status);
        }
        DeadlineManager manager = new DeadlineManager(repo, clock);
        var reminders = manager.listDeadlines();
        assertEquals(2, manager.checkDeadlines().size()); // Today and exactly seven days, not eight.
        assertEquals(5, reminders.size());
        assertEquals(List.of(DeadlineReminder.State.OVERDUE, DeadlineReminder.State.TODAY,
                DeadlineReminder.State.UPCOMING, DeadlineReminder.State.UPCOMING, DeadlineReminder.State.UNKNOWN),
                reminders.stream().map(DeadlineReminder::state).toList());
        assertEquals(-1, reminders.get(0).daysRemaining());
        assertEquals(0, reminders.get(1).daysRemaining());
        assertEquals(ZoneId.of("America/Toronto"), reminders.get(1).timezone());
    }

    @Test void observerDeliveryIsDeduplicatedSupportsUnsubscribeAndTracksUpdatedDeadlines() {
        Repository repo = new Repository(data);
        CareerFacade facade = new CareerFacade(repo);
        Job job = job(facade, "Due", today.plusDays(1));
        job(facade, "Overdue", today.minusDays(1));
        job(facade, "Distant", today.plusDays(8));
        DeadlineManager manager = new DeadlineManager(repo, clock);
        manager.checkDeadlines(); // No observers: do not consume notifications.
        List<DeadlineReminder> received = new ArrayList<>();
        DeadlineObserver observer = received::add;
        manager.subscribe(observer);
        manager.subscribe(observer);
        manager.subscribe(reminder -> { throw new IllegalStateException("Closed view"); });
        assertEquals(1, manager.checkDeadlines().size());
        manager.checkDeadlines();
        assertEquals(1, received.size());
        facade.importJob("Due", "Example", "Java skills", "", "Toronto", today.plusDays(2));
        manager.checkDeadlines();
        assertEquals(2, received.size());
        manager.unsubscribe(observer);
        facade.importJob("Due", "Example", "Java skills", "", "Toronto", today.plusDays(3));
        manager.checkDeadlines();
        assertEquals(2, received.size());
        manager.subscribe(observer); // Failed delivery must remain eligible for retry.
        manager.checkDeadlines();
        assertEquals(3, received.size());
        facade.trackApplication(job.getId(), today, Application.Status.APPLIED);
        assertTrue(manager.checkDeadlines().isEmpty());
    }
}
