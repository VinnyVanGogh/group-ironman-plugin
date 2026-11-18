import assert from "node:assert/strict";
import { hkdfSync } from "node:crypto";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { base32, base64url, GROUP_AUTH, GROUP_ID, groupAuth, groupId, hexToBytes } from "../src/keys.ts";

interface Vector {
	key: string;
	groupId: string;
	groupAuth: string;
}

const fixture = JSON.parse(readFileSync(import.meta.dirname + "/fixtures/keys.json", "utf8")) as {
	vectors: Vector[];
};

test("WebCrypto derivation matches node:crypto's independent HKDF", async () => {
	for (const v of fixture.vectors) {
		const ikm = hexToBytes(v.key);
		const id = base32(new Uint8Array(hkdfSync("sha256", ikm, new Uint8Array(0), "gimu/v1/id", 16)));
		const auth = base64url(new Uint8Array(hkdfSync("sha256", ikm, new Uint8Array(0), "gimu/v1/auth", 32)));
		assert.equal(await groupId(v.key), id);
		assert.equal(await groupAuth(v.key), auth);
	}
});

test("the shared fixture's vectors (the plugin's ServerKeysTest reads the same file)", async () => {
	for (const v of fixture.vectors) {
		assert.equal(await groupId(v.key), v.groupId, v.key);
		assert.equal(await groupAuth(v.key), v.groupAuth, v.key);
		assert.match(v.groupId, GROUP_ID);
		assert.match(v.groupAuth, GROUP_AUTH);
	}
});

test("a key that is not 32 lower-case hex characters is refused", () => {
	for (const bad of ["", "0123", "0123456789ABCDEF0123456789ABCDEF", "g".repeat(32)]) {
		assert.throws(() => hexToBytes(bad), bad);
	}
});

test("base32 of RFC 4648 test strings, lower case and unpadded", () => {
	const enc = (s: string) => base32(new TextEncoder().encode(s));
	assert.equal(enc("f"), "my");
	assert.equal(enc("foobar"), "mzxw6ytboi");
});
