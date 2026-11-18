// A Host for Hub over a real in-memory SQLite database (node:sqlite), with fake sockets that record
// what they were sent. Counts rows written the way Cloudflare bills them (rows changed), so the
// budget tests measure the same thing the free plan meters.

import { DatabaseSync } from "node:sqlite";
import type { Attachment, Conn, Host, Sql } from "../src/hub.ts";

export class CountingSql implements Sql {
	readonly db = new DatabaseSync(":memory:");
	rowsWritten = 0;
	rowsRead = 0;

	exec(query: string, ...params: unknown[]): { toArray(): Record<string, unknown>[] } {
		const stmt = this.db.prepare(query);
		const args = params as (string | number | null)[];
		if (/^\s*(SELECT)/i.test(query)) {
			const rows = stmt.all(...args) as Record<string, unknown>[];
			this.rowsRead += rows.length;
			return { toArray: () => rows };
		}
		const result = stmt.run(...args);
		this.rowsWritten += Number(result.changes);
		return { toArray: () => [] };
	}
}

export class FakeConn implements Conn {
	sent: string[] = [];
	closedWith: number | null = null;
	private att: Attachment | null = null;

	send(text: string): void {
		if (this.closedWith === null) {
			this.sent.push(text);
		}
	}

	close(code: number): void {
		this.closedWith = code;
	}

	attachment(): Attachment | null {
		return this.att === null ? null : { ...this.att };
	}

	attach(a: Attachment): void {
		this.att = { ...a };
	}

	frames(): Record<string, unknown>[] {
		return this.sent.map((s) => JSON.parse(s) as Record<string, unknown>);
	}

	/** Every payload relayed to this socket, as [from, payload]. */
	relayed(): [string, string][] {
		const out: [string, string][] = [];
		for (const f of this.frames()) {
			if (f.t === "b") {
				for (const m of f.m as { f: string; d: string }[]) {
					out.push([m.f, m.d]);
				}
			}
		}
		return out;
	}

	clear(): void {
		this.sent = [];
	}
}

export class FakeHost implements Host {
	sql = new CountingSql();
	open: FakeConn[] = [];
	time = Date.UTC(2026, 10, 18, 12, 0, 0);
	alarmAt: number | null = null;
	alarmsSet = 0;
	wiped = 0;
	pepper = "test-pepper";

	conns(): Conn[] {
		return this.open.filter((c) => c.closedWith === null);
	}

	now(): number {
		return this.time;
	}

	ensureAlarm(at: number): void {
		if (this.alarmAt === null) {
			this.setAlarm(at);
		}
	}

	setAlarm(at: number): void {
		this.alarmAt = at;
		this.alarmsSet++;
		// Cloudflare bills each setAlarm() as one row written.
		this.sql.rowsWritten++;
	}

	wipe(): void {
		this.wiped++;
		this.alarmAt = null;
		for (const t of ["grp", "members", "snap"]) {
			this.sql.db.exec(`DELETE FROM ${t}`);
		}
	}
}
