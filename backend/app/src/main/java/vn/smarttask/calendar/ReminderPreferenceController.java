package vn.smarttask.calendar;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.smarttask.common.ApiResult;
import vn.smarttask.common.AppException;
import vn.smarttask.common.service.AuditService;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.security.CurrentActor;
import vn.smarttask.security.RequiresPermission;

/**
 * F4 reminder preferences (docs EP-166/167, additive).
 *
 * <p>Scope note: the contract reads these fields from a
 * {@code user_profile_preference} table, which does not exist in this
 * codebase (V1..V12); the live source of truth is the {@code app_user}
 * profile columns ({@code quiet_hours_enabled}, {@code quiet_start},
 * {@code quiet_end}, {@code timezone}), so both endpoints operate on the
 * profile directly. GET includes the timezone; PUT also accepts an optional
 * validated IANA timezone. Existing clients may keep sending only the quiet-hours
 * triplet. {@code HH:MM} values are validated;
 * enabling quiet hours requires both endpoints of the window.
 */
@RestController
@RequestMapping("/api/v1/reminders/preferences")
public class ReminderPreferenceController {
  @org.springframework.beans.factory.annotation.Autowired(required=false) private org.springframework.jdbc.core.JdbcTemplate jdbc;
  @org.springframework.beans.factory.annotation.Autowired(required=false) private vn.smarttask.notify.service.NotificationProcessor processor;

  private final AppUserRepository users;
  private final AuditService audit;

  public ReminderPreferenceController(AppUserRepository users, AuditService audit) {
    this.users = users; this.audit = audit;
  }

  public record PreferencesDto(@JsonProperty("quiet_hours_enabled") boolean quietHoursEnabled,
      @JsonProperty("quiet_hours_start") String quietHoursStart,
      @JsonProperty("quiet_hours_end") String quietHoursEnd,
      @JsonProperty("timezone") String timezone) {}

  public record UpdateBody(@JsonProperty("quiet_hours_enabled") Boolean quietHoursEnabled,
      @JsonProperty("quiet_hours_start") String quietHoursStart,
      @JsonProperty("quiet_hours_end") String quietHoursEnd,
      @JsonProperty("timezone") String timezone) {
    public UpdateBody(Boolean enabled,String start,String end){this(enabled,start,end,null);}
  }

  private static String fmt(LocalTime t) {
    // Canonical HH:MM regardless of the TIME-column sub-minute precision.
    return t == null ? null : String.format("%02d:%02d", t.getHour(), t.getMinute());
  }

  private PreferencesDto toDto(vn.smarttask.identity.entity.AppUser u) {
    return new PreferencesDto(u.isQuietHoursEnabled(), fmt(u.getQuietStart()),
        fmt(u.getQuietEnd()), u.getTimezone());
  }

  /** EP-166: own reminder preferences (read-only, no audit per contract). */
  @GetMapping
  @RequiresPermission("calendar:read")
  @Transactional(readOnly = true)
  public ResponseEntity<ApiResult<PreferencesDto>> get() {
    UUID actor = CurrentActor.userId();
    var u = users.findById(actor).orElseThrow(() -> AppException.notFound("User not found"));
    return ResponseEntity.ok(ApiResult.ok(toDto(u), "reminder.preferences.success"));
  }

  /** EP-167: overwrite own quiet-hours window (audited). */
  @PutMapping
  @RequiresPermission("calendar:write")
  @Transactional
  public ResponseEntity<ApiResult<PreferencesDto>> update(@RequestBody UpdateBody body) {
    UUID actor = CurrentActor.userId();
    var u = users.findById(actor).orElseThrow(() -> AppException.notFound("User not found"));
    boolean enabled = body != null && body.quietHoursEnabled() != null
        ? body.quietHoursEnabled() : u.isQuietHoursEnabled();
    LocalTime start = parseHhMm(body == null ? null : body.quietHoursStart(), "start");
    LocalTime end = parseHhMm(body == null ? null : body.quietHoursEnd(), "end");
    if (start == null) start = u.getQuietStart();
    if (end == null) end = u.getQuietEnd();
    if (enabled && (start == null || end == null)) {
      audit.appendDenied(actor, "reminder.preferences.update", "app_user", actor,
          "QUIET_HOURS_INVALID");
      throw AppException.badRequest("QUIET_HOURS_INVALID",
          "quiet_hours_start/end (HH:MM) required when enabled");
    }
    u.setQuietHoursEnabled(enabled);
    if(body!=null&&body.timezone()!=null){try{u.setTimezone(java.time.ZoneId.of(body.timezone()).getId());}catch(java.time.DateTimeException e){throw AppException.badRequest("TIMEZONE_UNKNOWN","Unknown timezone");}}
    u.setQuietStart(start);
    u.setQuietEnd(end);
    users.save(u);
    if(jdbc!=null&&processor!=null){var until=processor.quietDeferral(u,"NORMAL");jdbc.update("update notification set quiet_hours_deferred_until=? where user_id=? and quiet_hours_deferred_until is not null and not redacted",until==null?java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC):until,actor);}
    audit.append(actor, "reminder.preferences.update", "app_user", actor,
        "ALLOWED", null, "{}");
    return ResponseEntity.ok(ApiResult.ok(toDto(u), "reminder.preferences.update.success"));
  }

  private static LocalTime parseHhMm(String v, String which) {
    if (v == null || v.isBlank()) return null;
    try {
      LocalTime t = LocalTime.parse(v.trim());
      if (t.getSecond() != 0 || t.getNano() != 0) {
        throw AppException.badRequest("QUIET_HOURS_INVALID",
            "quiet_hours_" + which + " must be HH:MM");
      }
      return t;
    } catch (DateTimeParseException e) {
      throw AppException.badRequest("QUIET_HOURS_INVALID",
          "quiet_hours_" + which + " must be HH:MM");
    }
  }
}
