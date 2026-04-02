package huynv.dlqreplayerservice.api;

import huynv.dlqreplayerservice.model.DlqEvent;
import huynv.dlqreplayerservice.model.DlqEventStatus;
import huynv.dlqreplayerservice.repository.DlqEventRepository;
import huynv.dlqreplayerservice.service.DlqReplayService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Exposes administrative APIs for DLQ inspection and replay.
 */
@RestController
public class DlqAdminController {

    private final DlqEventRepository dlqEventRepository;
    private final DlqReplayService dlqReplayService;

    /**
     * Creates an admin controller for DLQ inspection and replay.
     *
     * @param dlqEventRepository Repository used to query stored DLQ events.
     * @param dlqReplayService Replay service used to republish or skip stored DLQ events.
     * @return Initializes a DLQ admin controller instance.
     */
    public DlqAdminController(DlqEventRepository dlqEventRepository, DlqReplayService dlqReplayService) {
        this.dlqEventRepository = Objects.requireNonNull(dlqEventRepository, "dlqEventRepository");
        this.dlqReplayService = Objects.requireNonNull(dlqReplayService, "dlqReplayService");
    }

    /**
     * Lists stored DLQ events for inspection.
     *
     * @param status Status filter applied to stored DLQ events.
     * @param page Page index starting at 0.
     * @param size Page size.
     * @return Returns a page of DLQ event responses.
     */
    @GetMapping("/dlq/events")
    public Page<DlqEventResponse> list(
            @RequestParam(name = "status", defaultValue = "PENDING") DlqEventStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        Page<DlqEvent> events = dlqEventRepository.findByStatus(status, PageRequest.of(page, size));
        return events.map(DlqAdminController::toResponse);
    }

    /**
     * Replays a stored DLQ event back to its original topic.
     *
     * @param request Replay request identifying the stored DLQ event.
     * @return Performs a side effect by replaying the stored DLQ event.
     */
    @PostMapping("/dlq/replay")
    public void replay(@RequestBody DlqReplayRequest request) {
        dlqReplayService.replay(request.id(), request.overrideTopic());
    }

    /**
     * Marks a stored DLQ event as skipped.
     *
     * @param request Skip request identifying the stored DLQ event.
     * @return Performs a side effect by marking the stored DLQ event as skipped.
     */
    @PostMapping("/dlq/skip")
    public void skip(@RequestBody DlqReplayRequest request) {
        dlqReplayService.skip(request.id());
    }

    private static DlqEventResponse toResponse(DlqEvent event) {
        return new DlqEventResponse(
                event.getId(),
                event.getTopic(),
                event.getPartition(),
                event.getOffset(),
                event.getKey(),
                event.getOriginalTopic(),
                event.getStatus(),
                event.getCreatedAt()
        );
    }
}

