require("@nomicfoundation/hardhat-toolbox");
// Local development network only. No public network, no keys, no funds.
module.exports = { solidity: "0.8.24", networks: { hardhat: {}, localhost: { url: "http://127.0.0.1:8545" } } };
