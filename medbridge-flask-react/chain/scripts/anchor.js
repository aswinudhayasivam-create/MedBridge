// Usage: ANCHOR_ROOT=<hex64> ANCHOR_COUNT=<n> npx hardhat run scripts/anchor.js --network localhost
// Publishes a Merkle root to the LOCAL DEMO chain (hardhat node on 127.0.0.1:8545).
// Deploys the contract once and remembers its address in deployed.json.
// This is a throwaway local chain that we control. It proves nothing to third parties.
const fs = require("fs");
const path = require("path");
const { ethers } = require("hardhat");

async function main() {
  const [root, count] = [process.env.ANCHOR_ROOT, Number(process.env.ANCHOR_COUNT)];
  if (!/^[0-9a-f]{64}$/.test(root || "") || !(count > 0)) throw new Error("set ANCHOR_ROOT (64 hex chars) and ANCHOR_COUNT");
  const file = path.join(__dirname, "..", "deployed.json");
  const [signer] = await ethers.getSigners();
  const factory = await ethers.getContractFactory("AuditAnchor");
  let address = fs.existsSync(file) ? JSON.parse(fs.readFileSync(file)).address : null;
  if (address && (await ethers.provider.getCode(address)) === "0x") address = null; // chain was reset
  let c;
  if (!address) {
    c = await factory.deploy();
    await c.waitForDeployment();
    address = await c.getAddress();
    fs.writeFileSync(file, JSON.stringify({ address }));
  } else {
    c = factory.attach(address);
  }
  const tx = await c.anchor("0x" + root, count);
  const rc = await tx.wait();
  const id = Number(await c.count()) - 1;
  const stored = (await c.anchors(id)).root;
  console.log(JSON.stringify({ tx: rc.hash, id, contract: address, stored: stored.slice(2), signer: signer.address }));
}
main().catch((e) => { console.error(e.message); process.exit(1); });
