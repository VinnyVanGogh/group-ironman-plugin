// Prints the shared HKDF vectors for test/fixtures/keys.json, computed with node:crypto (not the
// WebCrypto code under test), so the fixture is an independent reference for both the server and
// the plugin's ServerKeysTest. Run: node scripts/vectors.ts
import { hkdfSync } from "node:crypto";
import { base32 } from "../src/keys.ts";

const KEYS = ["00000000000000000000000000000000", "0123456789abcdef0123456789abcdef", "ffffffffffffffffffffffffffffffff"];

const vectors = KEYS.map((key) => {
	const ikm = Buffer.from(key, "hex");
	const id = base32(new Uint8Array(hkdfSync("sha256", ikm, Buffer.alloc(0), "gimu/v1/id", 16)));
	const auth = Buffer.from(hkdfSync("sha256", ikm, Buffer.alloc(0), "gimu/v1/auth", 32)).toString("base64url");
	return { key, groupId: id, groupAuth: auth };
});
console.log(JSON.stringify(vectors, null, 2));
