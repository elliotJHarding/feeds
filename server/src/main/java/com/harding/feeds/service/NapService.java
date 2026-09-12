package com.harding.feeds.service;

import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.Baby;
import com.harding.feeds.entity.Nap;
import com.harding.feeds.repository.NapRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.harding.feeds.service.GroupScope.notFound;
import static com.harding.feeds.service.GroupScope.requireInGroup;

/**
 * Nap CRUD, group-scoped: every operation resolves the target baby/nap and
 * verifies it belongs to the caller's family group before touching it.
 * Deliberate mirror of {@link FeedService} - keep the two in step.
 *
 * <p>No NapChangedEvent: the Google Home device is computed purely from feeds,
 * so a nap change has nothing to report.
 */
@Service
public class NapService {

    private final NapRepository napRepository;
    private final BabyScope babyScope;
    private final InProgressEvents inProgressEvents;

    public NapService(NapRepository napRepository, BabyScope babyScope,
                      InProgressEvents inProgressEvents) {
        this.napRepository = napRepository;
        this.babyScope = babyScope;
        this.inProgressEvents = inProgressEvents;
    }

    @Transactional(readOnly = true)
    public List<Nap> getNaps(AppUser user, Long babyId, OffsetDateTime from, OffsetDateTime to,
                             OffsetDateTime updatedSince) {
        Baby baby = babyScope.require(user, babyId);
        return napRepository.findForBaby(baby, from, to, updatedSince);
    }

    /**
     * Idempotent by id: replaying a create whose id already exists in the
     * caller's group succeeds and returns the existing nap unchanged, so
     * offline sync retries are safe.
     */
    @Transactional
    public Creation create(AppUser user, UUID id, Long babyId,
                           OffsetDateTime startTime, OffsetDateTime endTime) {
        Optional<Nap> existing = napRepository.findById(id);
        if (existing.isPresent()) {
            Nap nap = existing.get();
            requireInGroup(user, nap.getBaby().getFamilyGroup(), "Nap");
            // A replay ends nothing. SyncEngine treats a 200 replay as a normal
            // outcome, so re-ending a feed the user has since restarted would be
            // a silent corruption reachable in ordinary use.
            return new Creation(nap, false);
        }

        Baby baby = babyScope.require(user, babyId);
        // Only a nap that is itself in progress claims the exclusive slot; a
        // completed nap is a retrospective log and must not end a live feed.
        if (endTime == null) {
            inProgressEvents.endAll(baby, startTime);
        }
        Nap nap = napRepository.save(new Nap(id, baby, startTime, endTime, user));
        return new Creation(nap, true);
    }

    /**
     * Full replacement of the editable fields. Any group member may update any
     * nap. Enforces no exclusivity: correcting history must never be refused,
     * and the codebase tolerates hand-edited overlaps everywhere else too.
     */
    @Transactional
    public Nap update(AppUser user, UUID id, Long babyId,
                      OffsetDateTime startTime, OffsetDateTime endTime) {
        Nap nap = scopedNap(user, id);

        nap.setBaby(babyScope.require(user, babyId));
        nap.setStartTime(startTime);
        nap.setEndTime(endTime);

        return napRepository.save(nap);
    }

    @Transactional
    public void delete(AppUser user, UUID id) {
        napRepository.delete(scopedNap(user, id));
    }

    private Nap scopedNap(AppUser user, UUID id) {
        Nap nap = napRepository.findById(id)
                .orElseThrow(() -> notFound("Nap not found"));
        requireInGroup(user, nap.getBaby().getFamilyGroup(), "Nap");
        return nap;
    }

    /** Outcome of an idempotent create: {@code created} is false on a replay. */
    public record Creation(Nap nap, boolean created) {
    }
}
