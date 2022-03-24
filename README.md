# Squeeth Crab Value Collector
A simple Java program that collects data from Ethereum mainnet and filters for `Hedge`, `HedgeUniswap`, `FlashWithdraw`, and `FlashDeposit` events to collect crab's value at that instant. The data is then outputted into a CSV which can then be used to manually graph out the value of Crab since inception.

# How to Use

In order to run the program, you need to pass 7 arguments when launching in the following order:
1. Ethereum Node HTTP URL
2. Crab Strategy Address
3. oSQTH Address
4. Squeeth Controller Address
5. oSQTH/ETH Uniswap v3 Pool Address
6. ETH/USDC Uniswap v3 Pool Address
7. Output CSV Path
