package vn.smarttask.task.service;
import java.time.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.smarttask.common.*;
import vn.smarttask.common.entity.IdempotencyRecord;
import vn.smarttask.common.repo.IdempotencyRecordRepository;
import vn.smarttask.common.service.TokenHash;
import vn.smarttask.security.PolicyEnforcer;
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.repo.TaskRepository;

@Service
public class TaskCreationService {
  public record Input(UUID projectId,String title,String description,String priority,UUID milestoneId,LocalDate dueDate,OffsetDateTime dueAt,String dueTimezone){}
  private final TaskService service;private final TaskRepository tasks;private final IdempotencyRecordRepository records;private final JdbcTemplate jdbc;private final ObjectMapper json;private final PolicyEnforcer policy;
  public TaskCreationService(TaskService service,TaskRepository tasks,IdempotencyRecordRepository records,JdbcTemplate jdbc,ObjectMapper json,PolicyEnforcer policy){this.service=service;this.tasks=tasks;this.records=records;this.jdbc=jdbc;this.json=json;this.policy=policy;}
  @Transactional
  public Task create(UUID actor,Input input,String key){
    if(key==null||key.isBlank()||key.length()>200)throw AppException.badRequest("IDEMPOTENCY_KEY_REQUIRED","Key must contain 1–200 characters");
    policy.requireProjectWrite(actor,input.projectId());String scoped="task.create:"+actor+":"+key;
    jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,scoped);
    try {
      String hash=TokenHash.sha256(json.writeValueAsString(input));var previous=records.findByKey(scoped);
      if(previous.isPresent()){
        var saved=json.readTree(previous.get().getResponseBody());
        if(!hash.equals(saved.path("hash").asText()))throw AppException.stateConflict("IDEMPOTENCY_CONFLICT","Key was used with a different task");
        return tasks.findById(UUID.fromString(saved.path("taskId").asText())).filter(t->input.projectId().equals(t.getProjectId())).orElseThrow(()->AppException.notFound("Task not found"));
      }
      var task=service.create(actor,input.projectId(),input.title(),input.description(),input.priority(),input.milestoneId());
      if(input.dueDate()!=null||input.dueAt()!=null)task=service.setDeadline(actor,task.getId(),input.dueDate(),input.dueAt(),input.dueTimezone(),task.getVersion());
      var record=new IdempotencyRecord(UuidV7.generate(),scoped,actor);record.setStatusCode(200);record.setResponseBody(json.writeValueAsString(java.util.Map.of("hash",hash,"taskId",task.getId())));records.save(record);return task;
    }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Invalid task creation record",e);}
  }
}
