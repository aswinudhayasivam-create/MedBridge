// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/// Stores Merkle roots of MedBridge custody events. Only the owner can add a root.
/// A root says "these records existed in this form at this block time". It says nothing
/// about whether the records were true. Personal data never goes here, only 32-byte roots.
contract AuditAnchor {
    address public immutable owner;
    struct Anchor { bytes32 root; uint64 timestamp; uint32 eventCount; }
    Anchor[] public anchors;
    event Anchored(uint256 indexed id, bytes32 root, uint32 eventCount);

    constructor() { owner = msg.sender; }

    function anchor(bytes32 root, uint32 eventCount) external returns (uint256 id) {
        require(msg.sender == owner, "only owner");
        require(root != bytes32(0) && eventCount > 0, "bad input");
        anchors.push(Anchor(root, uint64(block.timestamp), eventCount));
        id = anchors.length - 1;
        emit Anchored(id, root, eventCount);
    }

    function count() external view returns (uint256) { return anchors.length; }
}
