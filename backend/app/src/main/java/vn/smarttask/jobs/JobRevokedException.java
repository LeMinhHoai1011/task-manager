package vn.smarttask.jobs;

/** The scope behind a leased job was revoked mid-processing: cancel, never retry. */
public class JobRevokedException extends RuntimeException {
  private final String errorCode;

  public JobRevokedException(String errorCode) {
    super(errorCode);
    this.errorCode = errorCode;
  }

  public String getErrorCode() { return errorCode; }
}
