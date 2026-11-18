// The live socket's wire format (v1). Text frames only, JSON, except the literal "ping" the
// Durable Object answers "pong" to without waking (setWebSocketAutoResponse).
//
// client -> server
//   {"t":"b","d":[payload, ...]}          a batch of the plugin's own codec payloads (GroupCodec.java)
//   {"t":"b","d":[...],"p":1}             the same, sent once at logout: persist these now
//   {"t":"del"}                           delete this group's data (any member)
// server -> client
//   {"t":"hi","n":[name, ...]}            accepted; members online now (not counting you)
//   {"t":"b","m":[{"f":name,"d":payload}]} payloads from other members, live or stored
//   {"t":"j","n":name}                    a member connected: resend your live state
//   {"t":"l","n":name}                    a member disconnected
//   {"t":"e","c":code}                    a refusal that does not close the socket ("rate", "bad", "full")
//
// Payloads are stored and relayed opaquely after a shape check that mirrors GroupCodec's caps, so a
// new payload kind needs no server deploy.

/** GroupCodec.MAX_PAYLOAD_CHARS. */
export const MAX_PAYLOAD_CHARS = 6000;
/** GroupCodec.MAX_BANK_PARTS. */
export const MAX_BANK_PARTS = 16;
export const MAX_BATCH = 32;
/** One incoming frame; a full bank is 16 parts of 6,000 characters plus JSON. */
export const MAX_FRAME_CHARS = 128 * 1024;
/** Outgoing frames are split well under the 1 MiB Workers WebSocket message limit. */
export const MAX_OUT_CHARS = 256 * 1024;

/**
 * Kinds whose latest payload per member is kept, so a member who logs in later gets it while the
 * sender is offline. "durable" kinds change rarely and are written on every change; "live" kinds
 * change every few seconds while playing and are written at most once per member and kind per
 * LIVE_WRITE_MS (Board 2026-11-18), plus once at logout.
 */
export const DURABLE_KINDS = new Set(["bank", "storage", "roles", "gset", "gweb", "member", "shop", "qstate"]);
export const LIVE_KINDS = new Set(["stats", "carried", "extras"]);
export const LIVE_WRITE_MS = 5 * 60 * 1000;

const KIND = /^[a-z]{1,16}$/;

export interface Checked {
	payload: string;
	kind: string;
	/** Where the latest of this kind is kept for the sender, or null when it is only relayed. */
	slot: string | null;
	live: boolean;
	/** Bank only: how many parts this bank has, so parts left from a bigger bank are dropped. */
	parts?: number;
}

/** The payload if it is one the plugin could have sent, else null. */
export function checkPayload(payload: unknown): Checked | null {
	if (typeof payload !== "string" || payload.length === 0) {
		return null;
	}
	const rec = parseObject(payload, MAX_PAYLOAD_CHARS);
	const k = rec?.k;
	if (!rec || !isInt(rec.v) || rec.v < 1 || typeof k !== "string" || !KIND.test(k)) {
		return null;
	}
	return k === "bank" ? checkBank(payload, rec) : classify(payload, k);
}

/** A bank part: part p of n, both in range. */
function checkBank(payload: string, rec: Record<string, unknown>): Checked | null {
	const { p, n } = rec;
	if (!isInt(p) || !isInt(n) || n < 1 || n > MAX_BANK_PARTS || p < 0 || p >= n) {
		return null;
	}
	return { payload, kind: "bank", slot: "bank:" + p, live: false, parts: n };
}

function classify(payload: string, kind: string): Checked {
	const live = LIVE_KINDS.has(kind);
	const slot = live || DURABLE_KINDS.has(kind) ? kind : null;
	return { payload, kind, slot, live };
}

/** A JSON object of at most {@code max} characters, or null for anything else (arrays, scalars, bad JSON). */
function parseObject(text: string, max: number): Record<string, unknown> | null {
	if (text.length > max) {
		return null;
	}
	try {
		const o: unknown = JSON.parse(text);
		return typeof o === "object" && o !== null && !Array.isArray(o) ? (o as Record<string, unknown>) : null;
	} catch {
		return null;
	}
}

function isInt(x: unknown): x is number {
	return typeof x === "number" && Number.isInteger(x);
}

export type ClientFrame =
	| { t: "b"; d: Checked[]; persist: boolean; dropped: number }
	| { t: "del" }
	| null;

/** A client's frame, or null when it is not one. Bad payloads inside a good batch are dropped, not fatal. */
export function parseClientFrame(text: string): ClientFrame {
	const rec = parseObject(text, MAX_FRAME_CHARS);
	if (rec?.t === "del") {
		return { t: "del" };
	}
	const items = rec?.t === "b" ? rec.d : null;
	if (!rec || !Array.isArray(items) || items.length === 0 || items.length > MAX_BATCH) {
		return null;
	}
	const d = items.map(checkPayload).filter((c): c is Checked => c !== null);
	return { t: "b", d, persist: rec.p === 1, dropped: items.length - d.length };
}

export interface Relayed {
	f: string;
	d: string;
}

/** Payloads for one member, split into frames under MAX_OUT_CHARS. */
export function batchFrames(items: Relayed[]): string[] {
	const frames: string[] = [];
	let current: Relayed[] = [];
	let size = 0;
	for (const item of items) {
		const cost = item.d.length + item.f.length + 64;
		if (current.length > 0 && size + cost > MAX_OUT_CHARS) {
			frames.push(JSON.stringify({ t: "b", m: current }));
			current = [];
			size = 0;
		}
		current.push(item);
		size += cost;
	}
	if (current.length > 0) {
		frames.push(JSON.stringify({ t: "b", m: current }));
	}
	return frames;
}

/** A RuneScape name as the plugin sends it: 1-12 characters of letters, digits, spaces, '-' and '_'. */
const NAME = /^[A-Za-z0-9 _\- ]{1,12}$/;

/** GroupMembers.key: no-break spaces as spaces, trimmed, then '_' and '-' as spaces, lower case. */
export function nameKey(name: string): string {
	return name.replace(/ /g, " ").trim().replace(/[_-]/g, " ").toLowerCase();
}

export function validName(name: string | null): name is string {
	return name !== null && NAME.test(name) && nameKey(name).length > 0;
}
