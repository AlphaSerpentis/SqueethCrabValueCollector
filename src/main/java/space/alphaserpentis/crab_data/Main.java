package space.alphaserpentis.crab_data;

import io.reactivex.annotations.Nullable;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint128;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.abi.datatypes.generated.Uint96;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

public class Main {

    public static final String zeroAddress = "0x0000000000000000000000000000000000000000";

    public static volatile BigInteger lastBlock = BigInteger.ZERO;

    public static DefaultBlockParameterNumber startBlockSearch;
    public static PrintWriter outputFile;
    public static Web3j web3;
    public static EthFilter filter;
    public static String crab, oSQTH, pool, ethusdcPool, oracle, usdc = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48", weth = "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2";
    public static ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    public static ScheduledFuture<?> future = null;

    public static void main(String[] args) throws Exception {
        if(args.length != 6)
             throw new Exception("Missing arguments, please pass the following: \n\n(0) node HTTP URL, (1) true/false if crab v2, (2) ethusdc pool address, (3) starting block for search, (4) ending block for search, (5) output file path");
        else {
            HttpService node = new HttpService(args[0]);
            web3 = Web3j.build(node);

            startBlockSearch = new DefaultBlockParameterNumber(Long.parseLong(args[3]));

            if(Boolean.parseBoolean(args[1])) {
                crab = "0x3b960e47784150f5a63777201ee2b15253d713e8";
                filter = new EthFilter(startBlockSearch, DefaultBlockParameterName.LATEST, crab).addOptionalTopics("0xbbc3ba742efe346cfdf333000069964e0ee3087c68da267dac977d299f2366fb","0x5d85169ff8329e90f3225f9798e0eba54d00c55d3bbfe201a0d1606febb23a8e","0xa13b272c1cf13ba724064d3d4809d9f557aab8da2bb582cba031a2f57e728e9d");
            } else {
                crab = "0xf205ad80bb86ac92247638914265887a8baa437d";
                if(args[4].isBlank() || Long.parseLong(args[4]) == 0 || Long.parseLong(args[4]) < Long.parseLong(args[3]))
                    filter = new EthFilter(startBlockSearch, DefaultBlockParameterName.LATEST, crab).addOptionalTopics("0x878fd3ca52ad322c7535f559ee7c91afc67363073783360ef1b1420589dc6174", "0x4c1a959210172325f5c6678421c3834b04ae8ce57f7a7c0c0bbfbb62bca37e34", "0xa13b272c1cf13ba724064d3d4809d9f557aab8da2bb582cba031a2f57e728e9d", "0x5d85169ff8329e90f3225f9798e0eba54d00c55d3bbfe201a0d1606febb23a8e");
                else
                    filter = new EthFilter(startBlockSearch, new DefaultBlockParameterNumber(Long.parseLong(args[4])), crab).addOptionalTopics("0x878fd3ca52ad322c7535f559ee7c91afc67363073783360ef1b1420589dc6174", "0x4c1a959210172325f5c6678421c3834b04ae8ce57f7a7c0c0bbfbb62bca37e34", "0xa13b272c1cf13ba724064d3d4809d9f557aab8da2bb582cba031a2f57e728e9d", "0x5d85169ff8329e90f3225f9798e0eba54d00c55d3bbfe201a0d1606febb23a8e");
            }

            ethusdcPool = args[2];

            callAndSetOtherContracts();

            outputFile = new PrintWriter(args[5]);

            StringBuilder sb = new StringBuilder();
            sb.append("Timestamp").append(",").append("ETH/USD").append(",").append("Crab/ETH").append(",").append("Crab/USD").append(",").append("Hedged?").append('\n');

            outputFile.print(sb);

            outputFile.flush();

            web3.ethLogFlowable(filter).subscribe(
                    log -> {

                        System.out.println("Block " + log.getBlockNumber().toString());

                        if(future != null)
                            future.cancel(false);

                        if(lastBlock.equals(log.getBlockNumber())) {
                            return;
                        } else if(log.getBlockNumber().subtract(lastBlock).compareTo(BigInteger.valueOf(125)) > 0 && !lastBlock.equals(BigInteger.ZERO)) {
                            long timesToRepeat = log.getBlockNumber().subtract(lastBlock).divide(BigInteger.valueOf(125)).longValue();

                            System.out.println(timesToRepeat);
                            System.out.println(log.getBlockNumber() + " + " + lastBlock);

                            BigInteger lowerBlock = lastBlock;

                            for(int i = 1; i <= timesToRepeat; i++) {
                                BigInteger block = BigInteger.valueOf(lowerBlock.longValue() + (125L * i));
                                System.out.println("Collecting Block " + block);
                                runStuff(block, null, Boolean.parseBoolean(args[1]));
                            }
                        }

                        runStuff(log.getBlockNumber(), log.getTopics(), Boolean.parseBoolean(args[1]));

                        future = scheduledExecutor.schedule(() -> {
                            try {
                                System.out.println("SCHEDULED");
                                BigInteger currentLatestBlock = web3.ethBlockNumber().send().getBlockNumber();
                                long timesToRepeat = currentLatestBlock.subtract(lastBlock).divide(BigInteger.valueOf(125)).longValue();

                                System.out.println(timesToRepeat);
                                System.out.println(currentLatestBlock + " + " + lastBlock);

                                BigInteger lowerBlock = lastBlock;

                                for(int i = 1; i <= timesToRepeat; i++) {
                                    BigInteger block = BigInteger.valueOf(lowerBlock.longValue() + (125L * i));
                                    System.out.println("Collecting Block " + block);
                                    runStuff(block, null, Boolean.parseBoolean(args[1]));
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }, 5, TimeUnit.SECONDS);
                    }
            );
        }
    }

    public static void runStuff(BigInteger blockNumber, @Nullable List<String> topics, boolean isCrabv2) throws IOException {
        lastBlock = blockNumber;

        EthBlock block = web3.ethGetBlockByNumber(new DefaultBlockParameterNumber(blockNumber), false).send();
        boolean hedged = false;

        if(topics != null) {
            for(String s: topics) {
                if(isCrabv2) {
                    if(s.equalsIgnoreCase("0xbbc3ba742efe346cfdf333000069964e0ee3087c68da267dac977d299f2366fb")) {
                        hedged = true;
                        break;
                    }
                } else {
                    if(s.equalsIgnoreCase("0x4c1a959210172325f5c6678421c3834b04ae8ce57f7a7c0c0bbfbb62bca37e34") || s.equalsIgnoreCase("0x878fd3ca52ad322c7535f559ee7c91afc67363073783360ef1b1420589dc6174")) {
                        hedged = true;
                        break;
                    }
                }
            }
        }

        try {
            Double[] prices = calculatePriceAtBlock(blockNumber);
            System.out.println(Arrays.toString(prices));
            writeToFile(block.getBlock().getTimestamp().doubleValue(), prices[0], prices[1], prices[2], hedged);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public static void writeToFile(Double block, Double ethPrice, Double ethTerms, Double usdTerms, boolean hedged) {
        StringBuilder sb = new StringBuilder();
        sb.append(block).append(',').append(ethPrice).append(',').append(ethTerms).append(',').append(usdTerms).append(',').append(hedged).append('\n');

        outputFile.print(sb);

        outputFile.flush();
    }

    public static void callAndSetOtherContracts() throws ExecutionException, InterruptedException {
        Function callCrabOracle = new Function("oracle",
                Collections.emptyList(),
                List.of(
                        new TypeReference<Address>() {
                        }
                )
        );
        Function callwPowerPerp = new Function("wPowerPerp",
                Collections.emptyList(),
                List.of(
                        new TypeReference<Address>() {
                        }
                )
        );
        Function callethWSqueethPool = new Function("ethWSqueethPool",
                Collections.emptyList(),
                List.of(
                        new TypeReference<Address>() {
                        }
                )
        );

        oracle = (String) FunctionReturnDecoder.decode(
                web3.ethCall(
                        Transaction.createEthCallTransaction(
                                zeroAddress,
                                crab,
                                FunctionEncoder.encode(callCrabOracle)
                        ),
                        startBlockSearch
                ).sendAsync().get().getResult(),
                callCrabOracle.getOutputParameters()
        ).get(0).getValue();

        oSQTH = (String) FunctionReturnDecoder.decode(
                web3.ethCall(
                        Transaction.createEthCallTransaction(
                                zeroAddress,
                                crab,
                                FunctionEncoder.encode(callwPowerPerp)
                        ),
                        startBlockSearch
                ).sendAsync().get().getResult(),
                callwPowerPerp.getOutputParameters()
        ).get(0).getValue();

        pool = (String) FunctionReturnDecoder.decode(
                web3.ethCall(
                        Transaction.createEthCallTransaction(
                                zeroAddress,
                                crab,
                                FunctionEncoder.encode(callethWSqueethPool)
                        ),
                        startBlockSearch
                ).sendAsync().get().getResult(),
                callethWSqueethPool.getOutputParameters()
        ).get(0).getValue();

    }

    public static Double[] calculatePriceAtBlock(BigInteger block) throws ExecutionException, InterruptedException {
        BigInteger value, shortAmount, collateralAmount, priceOfoSQTH, priceOfETHinUSD, ethTerms, totalSupply;

        // Functions to call

        Function callVaultsFunc = new Function("getVaultDetails",
                Collections.emptyList(),
                Arrays.asList(
                        new TypeReference<Address>() { },
                        new TypeReference<Uint32>() { },
                        new TypeReference<Uint96>() { },
                        new TypeReference<Uint128>() { }
                )
        );
        Function callUniswapv3PriceCheck = new Function("getTwap",
                Arrays.asList(
                        new org.web3j.abi.datatypes.Address(pool),
                        new org.web3j.abi.datatypes.Address(oSQTH),
                        new org.web3j.abi.datatypes.Address(weth),
                        new Uint32(1),
                        new org.web3j.abi.datatypes.Bool(true)
                ),
                Arrays.asList(
                        new TypeReference<Uint256>() { }
                )
        );
        Function callUniswapv3PriceCheck_USDC = new Function("getTwap",
                Arrays.asList(
                        new org.web3j.abi.datatypes.Address(ethusdcPool),
                        new org.web3j.abi.datatypes.Address(weth),
                        new org.web3j.abi.datatypes.Address(usdc),
                        new Uint32(1),
                        new org.web3j.abi.datatypes.Bool(true)
                ),
                Arrays.asList(
                        new TypeReference<Uint256>() { }
                )
        );
        Function callTotalSupply = new Function("totalSupply",
                Collections.emptyList(),
                Arrays.asList(new TypeReference<Uint256>() { }
                )
        );

        // Call

        EthCall response_vaultsFunc = web3.ethCall(
                Transaction.createEthCallTransaction(
                        zeroAddress,
                        crab,
                        FunctionEncoder.encode(callVaultsFunc)
                ),
                new DefaultBlockParameterNumber(Long.parseLong(block.toString()))
        ).sendAsync().get();
        EthCall response_uniswapv3PriceCheck = web3.ethCall(
                Transaction.createEthCallTransaction(
                        zeroAddress,
                        oracle,
                        FunctionEncoder.encode(callUniswapv3PriceCheck)
                ),
                new DefaultBlockParameterNumber(Long.parseLong(block.toString()))
        ).sendAsync().get();

        EthCall response_uniswapv3PriceCheck_USDC = web3.ethCall(
                Transaction.createEthCallTransaction(
                        zeroAddress,
                        oracle,
                        FunctionEncoder.encode(callUniswapv3PriceCheck_USDC)
                ),
                new DefaultBlockParameterNumber(Long.parseLong(block.toString()))
        ).sendAsync().get();

        EthCall response_totalSupply = web3.ethCall(
                Transaction.createEthCallTransaction(
                        zeroAddress,
                        crab,
                        FunctionEncoder.encode(callTotalSupply)
                ),
                new DefaultBlockParameterNumber(Long.parseLong(block.toString()))
        ).sendAsync().get();

        // Decode

        List<Type> decodedList_0 = FunctionReturnDecoder.decode(
                response_vaultsFunc.getResult(),
                callVaultsFunc.getOutputParameters()
        );

        List<Type> decodedList_1 = FunctionReturnDecoder.decode(
                response_uniswapv3PriceCheck.getResult(),
                callUniswapv3PriceCheck.getOutputParameters()
        );

        List<Type> decodedList_2 = FunctionReturnDecoder.decode(
                response_uniswapv3PriceCheck_USDC.getResult(),
                callUniswapv3PriceCheck_USDC.getOutputParameters()
        );

        List<Type> decodedList_3 = FunctionReturnDecoder.decode(
                response_totalSupply.getResult(),
                callTotalSupply.getOutputParameters()
        );

        // Set Values

        collateralAmount = (BigInteger)  decodedList_0.get(2).getValue();
        shortAmount = (BigInteger) decodedList_0.get(3).getValue();
        priceOfoSQTH = (BigInteger) decodedList_1.get(0).getValue();
        priceOfETHinUSD = (BigInteger) decodedList_2.get(0).getValue();
        totalSupply = (BigInteger) decodedList_3.get(0).getValue();

        // Takes ETH and USD net value respectively
        ethTerms = collateralAmount.subtract(shortAmount.multiply(priceOfoSQTH).divide(BigInteger.valueOf((long) Math.pow(10,18))));
        value = ethTerms.multiply(priceOfETHinUSD).divide(BigInteger.valueOf((long) Math.pow(10,18)));

        return new Double[] {
            priceOfETHinUSD.doubleValue() / Math.pow(10,18),
            ethTerms.multiply(BigInteger.valueOf((long) Math.pow(10,18))).divide(totalSupply).doubleValue() / Math.pow(10, 18),
            value.multiply(BigInteger.valueOf((long) Math.pow(10,18))).divide(totalSupply).doubleValue() / Math.pow(10, 18)
        };
    }
}
