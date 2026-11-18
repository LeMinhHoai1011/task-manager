package vn.smarttask.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.data.jpa.domain.Specification;
import vn.smarttask.common.AppException;
import vn.smarttask.task.entity.Task;

/** Keyset cursor retains every sort value, null and timestamp precision. */
public final class TaskCursor {
  private TaskCursor() {}
  private static final ObjectMapper JSON = new ObjectMapper();
  public record Boundary(String sort, List<String> values, UUID id) {}

  public static String nextCursor(Task last, String sort, String order) {
    try {
      var values = new ArrayList<String>();
      for (var key : TaskSort.parse(sort, order)) {
        var value = TaskSort.value(last, key.field());
        values.add(value == null ? null : value.toString());
      }
      return "v2." + Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.writeValueAsBytes(
          new Boundary(TaskSort.canonical(sort, order), values, last.getId())));
    } catch (java.io.IOException e) { throw new IllegalStateException("Cannot encode cursor", e); }
  }

  public static Boundary decode(String cursor, String sort, String order) {
    try {
      if (cursor.length() > 8192 || !cursor.startsWith("v2.")) throw new IllegalArgumentException();
      var value = JSON.readValue(Base64.getUrlDecoder().decode(cursor.substring(3)), Boundary.class);
      var keys = TaskSort.parse(sort, order);
      if (value.id() == null || !TaskSort.canonical(sort, order).equals(value.sort())
          || value.values() == null || value.values().size() != keys.size()) throw new IllegalArgumentException();
      for (int i=0;i<keys.size();i++) typed(value.values().get(i), keys.get(i).field());
      return value;
    } catch (Exception e) { throw AppException.badRequest("CURSOR_INVALID", "Cursor does not match this sort"); }
  }

  public static UUID legacyId(String cursor) {
    try {
      String[] parts = cursor.split("\\|");
      return UUID.fromString(cursor.startsWith("v1|") && parts.length == 5 ? parts[4] : cursor);
    } catch (RuntimeException e) { throw AppException.badRequest("CURSOR_INVALID", "Invalid cursor"); }
  }

  private static Comparable<?> typed(String value, String field) {
    if (value == null) return null;
    return switch(field) {
      case "priority" -> Integer.valueOf(value);
      case "title", "state" -> value;
      default -> OffsetDateTime.parse(value);
    };
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static Specification<Task> after(Boundary boundary, String sort, String order) {
    var keys = TaskSort.parse(sort, order);
    return (task, query, cb) -> {
      var equal = cb.conjunction();
      var result = cb.disjunction();
      for (int i=0;i<keys.size();i++) {
        var key = keys.get(i);
        var expression = TaskSort.expression(task, cb, key.field());
        Comparable value = typed(boundary.values().get(i), key.field());
        jakarta.persistence.criteria.Predicate greater;
        if (value == null) greater = key.desc() ? cb.isNotNull(expression) : cb.disjunction();
        else {
          greater = key.desc() ? cb.lessThan((jakarta.persistence.criteria.Expression) expression, value)
              : cb.greaterThan((jakarta.persistence.criteria.Expression) expression, value);
          if (!key.desc()) greater = cb.or(greater, cb.isNull(expression));
        }
        result = cb.or(result, cb.and(equal, greater));
        equal = cb.and(equal, value == null ? cb.isNull(expression) : cb.equal(expression, value));
      }
      var idAfter = keys.get(0).desc() ? cb.lessThan(task.<UUID>get("id"), boundary.id())
          : cb.greaterThan(task.<UUID>get("id"), boundary.id());
      return cb.or(result, cb.and(equal, idAfter));
    };
  }

  /** Compatibility for consumers that already hold a sorted list. */
  @SuppressWarnings({"rawtypes", "unchecked"})
  public static int resolveStart(List<Task> sorted, String cursor, String sort, String order) {
    if (cursor == null || cursor.isBlank()) return 0;
    if (!cursor.startsWith("v2.")) {
      var id = legacyId(cursor);
      for (int i=0;i<sorted.size();i++) if(sorted.get(i).getId().equals(id)) return i+1;
      return 0;
    }
    var boundary = decode(cursor, sort, order);
    var keys = TaskSort.parse(sort,order);
    for (int i=0;i<sorted.size();i++) {
      Task task = sorted.get(i);
      int comparison=0;
      for (int n=0;n<keys.size() && comparison==0;n++) {
        Comparable a=TaskSort.value(task,keys.get(n).field()), b=typed(boundary.values().get(n),keys.get(n).field());
        comparison=a==null?(b==null?0:1):b==null?-1:a.compareTo(b);
        if(keys.get(n).desc())comparison=-comparison;
      }
      if(comparison==0) {
        comparison=task.getId().toString().compareTo(boundary.id().toString());
        if(keys.get(0).desc())comparison=-comparison;
      }
      if(comparison>0)return i;
    }
    return sorted.size();
  }
}
