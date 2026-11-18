// What a client derives from its 128-bit group key (GroupKey.java). The server never sees the key:
// it gets the groupId in the URL and the groupAuth as a bearer secret. The plugin's ServerKeys.java
// derives the same values; test/fixtures/keys.json holds the vectors both sides must reproduce.

const encoder = new TextEncoder();
const BASE32 = "abcdefghijklmnopqrstuvwxyz234567";

export const ID_INFO = "gimu/v1/id";
export const AUTH_INFO = "gimu/v1/auth";

/** A group id: 16 HKDF bytes as unpadded lower-case base32, 26 characters. */
export const GROUP_ID = /^[a-z2-7]{26}$/;
/** A group auth: 32 HKDF bytes as unpadded base64url, 43 characters. */
export const GROUP_AUTH = /^[A-Za-z0-9_-]{43}$/;

export function hexToBytes(hex: string): Uint8Array {
	if (!/^[0-9a-f]{32}$/.test(hex)) {
		throw new Error("not a group key");
	}
	const out = new Uint8Array(16);
	for (let i = 0; i < 16; i++) {
		out[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
	}
	return out;
}

export async function hkdf(ikm: Uint8Array, info: string, length: number): Promise<Uint8Array> {
	const key = await crypto.subtle.importKey("raw", ikm, "HKDF", false, ["deriveBits"]);
	const bits = await crypto.subtle.deriveBits(
		{ name: "HKDF", hash: "SHA-256", salt: new Uint8Array(0), info: encoder.encode(info) },
		key,
		length * 8,
	);
	return new Uint8Array(bits);
}

export function base32(bytes: Uint8Array): string {
	let out = "";
	let buffer = 0;
	let bits = 0;
	for (const b of bytes) {
		buffer = (buffer << 8) | b;
		bits += 8;
		while (bits >= 5) {
			out += BASE32[(buffer >>> (bits - 5)) & 31];
			bits -= 5;
		}
	}
	if (bits > 0) {
		out += BASE32[(buffer << (5 - bits)) & 31];
	}
	return out;
}

export function base64url(bytes: Uint8Array): string {
	let s = "";
	for (const b of bytes) {
		s += String.fromCharCode(b);
	}
	return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function fromBase64url(text: string): Uint8Array {
	const s = atob(text.replace(/-/g, "+").replace(/_/g, "/"));
	const out = new Uint8Array(s.length);
	for (let i = 0; i < s.length; i++) {
		out[i] = s.charCodeAt(i);
	}
	return out;
}

export async function groupId(keyHex: string): Promise<string> {
	return base32(await hkdf(hexToBytes(keyHex), ID_INFO, 16));
}

export async function groupAuth(keyHex: string): Promise<string> {
	return base64url(await hkdf(hexToBytes(keyHex), AUTH_INFO, 32));
}
