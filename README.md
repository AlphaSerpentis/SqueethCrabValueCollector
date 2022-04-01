# Squeeth Crab Value Collector
A simple Java program that collects data from Ethereum mainnet and filters for `Hedge`, `HedgeUniswap`, `FlashWithdraw`, and `FlashDeposit` events to collect crab's value at that instant. The data is then outputted into a CSV which can then be used to manually graph out the value of Crab since inception.

# How to Use

In order to run the program, you need to pass 6 arguments when launching in the following order:
1. Ethereum Node HTTP URL
2. Crab Strategy Address
3. ETH/USDC Uniswap v3 Pool Address
4. Starting Block for Search
5. Ending Block for Search
6. Output CSV Path

Once the program is done running, you should see the output stop. You can then close the program and use the CSV for whatever you need it for.
