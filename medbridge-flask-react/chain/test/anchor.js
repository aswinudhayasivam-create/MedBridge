const { expect } = require("chai");
const { ethers } = require("hardhat");

describe("AuditAnchor", () => {
  it("stores roots in order and emits events", async () => {
    const [owner] = await ethers.getSigners();
    const c = await (await ethers.getContractFactory("AuditAnchor")).deploy();
    const root = "0x" + "ab".repeat(32);
    await expect(c.anchor(root, 3)).to.emit(c, "Anchored").withArgs(0, root, 3);
    expect(await c.count()).to.equal(1);
    expect((await c.anchors(0)).root).to.equal(root);
    expect(await c.owner()).to.equal(owner.address);
  });
  it("rejects non-owners and empty input", async () => {
    const [, other] = await ethers.getSigners();
    const c = await (await ethers.getContractFactory("AuditAnchor")).deploy();
    await expect(c.connect(other).anchor("0x" + "01".repeat(32), 1)).to.be.revertedWith("only owner");
    await expect(c.anchor("0x" + "00".repeat(32), 1)).to.be.revertedWith("bad input");
    await expect(c.anchor("0x" + "01".repeat(32), 0)).to.be.revertedWith("bad input");
  });
  it("the chain operator can reset it: a local chain proves nothing to third parties", async () => {
    // Demonstrates the honest limit: a fresh local chain simply has no history.
    const c = await (await ethers.getContractFactory("AuditAnchor")).deploy();
    expect(await c.count()).to.equal(0);
  });
});
