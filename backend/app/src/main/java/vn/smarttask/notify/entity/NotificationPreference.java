package vn.smarttask.notify.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "notification_preference")
public class NotificationPreference {
  @Id private UUID id;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(name = "type", nullable = false) private String type;
  @Column(name = "channel_in_app", nullable = false) private boolean channelInApp = true;
  @Column(name = "channel_push", nullable = false) private boolean channelPush = true;
  @Column(name = "channel_email", nullable = false) private boolean channelEmail = false;
  @Column(name = "muted", nullable = false) private boolean muted = false;

  protected NotificationPreference() {}
  public NotificationPreference(UUID id, UUID userId, String type) {
    this.id = id; this.userId = userId; this.type = type;
  }
  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getType() { return type; }
  public boolean isMuted() { return muted; }
  public void setMuted(boolean v) { this.muted = v; }
  public boolean isChannelInApp() { return channelInApp; }
  public void setChannelInApp(boolean v) { this.channelInApp = v; }
  public boolean isChannelPush() { return channelPush; }
  public void setChannelPush(boolean v) { this.channelPush = v; }
  public boolean isChannelEmail() { return channelEmail; }
  public void setChannelEmail(boolean v) { this.channelEmail = v; }
}
