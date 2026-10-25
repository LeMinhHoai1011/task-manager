package vn.smarttask.ai;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import vn.smarttask.ai.entity.AiContextManifest;
import vn.smarttask.ai.entity.AiInteraction;
import vn.smarttask.ai.entity.AiJob;
import vn.smarttask.ai.entity.AiSuggestion;
import vn.smarttask.ai.repo.AiContextManifestRepository;
import vn.smarttask.ai.repo.AiInteractionRepository;
import vn.smarttask.ai.repo.AiJobRepository;
import vn.smarttask.ai.repo.AiModelConfigurationRepository;
import vn.smarttask.ai.repo.AiSuggestionRepository;
import vn.smarttask.ai.service.AiService;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.task.service.TaskService;

/** Shared Mockito-only fixture (no DB, no GPU, no Docker). */
class AiFixture {
  final AiJobRepository jobs = mock(AiJobRepository.class);
  final AiSuggestionRepository suggestions = mock(AiSuggestionRepository.class);
  final AiContextManifestRepository manifests = mock(AiContextManifestRepository.class);
  final AiModelConfigurationRepository configs = mock(AiModelConfigurationRepository.class);
  final AiInteractionRepository interactions = mock(AiInteractionRepository.class);
  final ProjectApi projectApi = mock(ProjectApi.class);
  final TaskService taskService = mock(TaskService.class);
  final AuditService audit = mock(AuditService.class);
  final MockOllamaClient ollama;
  final AiService service;
  final AtomicReference<AiJob> jobRef = new AtomicReference<>();
  final AtomicReference<AiSuggestion> sugRef = new AtomicReference<>();
  final Set<UUID> activeUsers = ConcurrentHashMap.newKeySet();
  final Set<UUID> participants = ConcurrentHashMap.newKeySet();
  final UUID user = UUID.randomUUID();
  final UUID projectId = UUID.randomUUID();
  final AiContextManifest manifest = new AiContextManifest("TASK_DRAFT");

  AiFixture(MockOllamaClient.Mode mode) {
    ollama = new MockOllamaClient(mode);
    manifest.setAllowedFields("[\"title\",\"description\",\"priority\",\"assignee_user_id\",\"subtasks\"]");
    manifest.setForbiddenPatterns("[\"password*\",\"secret*\"]");
    manifest.setEnabled(true);
    activeUsers.add(user);
    participants.add(user);

    when(manifests.findById("TASK_DRAFT")).thenReturn(Optional.of(manifest));
    when(configs.findById(anyString())).thenReturn(Optional.empty());
    when(jobs.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
    when(jobs.save(any(AiJob.class))).thenAnswer(i -> {
      AiJob j = i.getArgument(0);
      jobRef.set(j);
      return j;
    });
    when(jobs.findById(any(UUID.class))).thenAnswer(i -> Optional.ofNullable(jobRef.get()));
    when(suggestions.save(any(AiSuggestion.class))).thenAnswer(i -> {
      AiSuggestion s = i.getArgument(0);
      sugRef.set(s);
      return s;
    });
    when(suggestions.findByJobId(any(UUID.class))).thenAnswer(i -> Optional.ofNullable(sugRef.get()));
    when(interactions.save(any(AiInteraction.class))).thenAnswer(i -> i.getArgument(0));
    when(projectApi.isActiveUser(any(UUID.class))).thenAnswer(i -> activeUsers.contains(i.getArgument(0)));
    when(projectApi.hasParticipation(any(UUID.class), any(UUID.class))).thenAnswer(i ->
        projectId.equals(i.getArgument(0)) && participants.contains(i.getArgument(1)));

    service = new AiService(jobs, suggestions, manifests, configs, interactions, projectApi,
        taskService, ollama, audit, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
        new ObjectMapper());
  }

  AiJob requestOk() {
    return service.request(user, AiFeature.TASK_DRAFT, projectId, "help draft login task",
        List.of("title"), "idem-" + UUID.randomUUID());
  }
}
