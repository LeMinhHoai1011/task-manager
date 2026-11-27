import {useState} from "react";
import {apiFetch, unwrapData} from "../api/client";

type Schedule = {interval_days?: number; timezone?: string; next_date?: string; stopped_at?: string};

export default function PersonalRecurrence({id, version, due}: {id: string; version: number; due?: string}) {
  const [schedule, setSchedule] = useState<Schedule | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const path = `/me/task-recurrences/${id}`;
  const active = schedule?.interval_days && !schedule.stopped_at;

  async function request(method: "GET" | "PUT" | "DELETE", intervalDays?: number) {
    setBusy(true);
    setError("");
    try {
      const result = await apiFetch(path, {method, body: intervalDays ? {intervalDays, version} : undefined});
      setSchedule(unwrapData<Schedule>(result) ?? {});
    } catch (e) {
      setError((e as {uiMessage?: string})?.uiMessage ?? "Không cập nhật được lịch lặp.");
    } finally {
      setBusy(false);
    }
  }

  return <section className="space-y-2">
    <h2>Lịch lặp</h2>
    {error && <p role="alert">{error}</p>}
    {schedule === null ? <button className="btn" disabled={busy} onClick={() => void request("GET")}>Quản lý lịch lặp</button>
      : active ? <>
        <p>{schedule.interval_days === 1 ? "Hằng ngày" : "Hằng tuần"} · Kỳ tiếp theo {schedule.next_date} ({schedule.timezone})</p>
        <button className="btn" disabled={busy} onClick={() => void request("DELETE")}>Dừng lặp</button>
      </> : due ? <div className="flex flex-wrap gap-2">
        <button className="btn" disabled={busy} onClick={() => void request("PUT", 1)}>Lặp hằng ngày</button>
        <button className="btn" disabled={busy} onClick={() => void request("PUT", 7)}>Lặp hằng tuần</button>
      </div> : <p>Đặt hạn cho việc trước khi bật lặp.</p>}
  </section>;
}
