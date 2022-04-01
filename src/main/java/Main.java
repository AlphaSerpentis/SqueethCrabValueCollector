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

import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class Main {

    public static final String zeroAddress = "0x0000000000000000000000000000000000000000";

    public static volatile BigInteger lastBlock = new BigInteger("0");

    public static DefaultBlockParameterNumber startBlockSearch;
    public static PrintWriter outputFile;
    public static Web3j web3;
    public static EthFilter filter;
    public static String crab, oSQTH, pool, ethusdcPool, oracle, usdc = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48", weth = "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2";

    public static void main(String[] args) throws Exception {
        if(args.length != 6)
             throw new Exception("Missing arguments, please pass the following: \n\n(0) node HTTP URL, (1) crab address, (2) ethusdc pool address, (3) starting block for search, (4) ending block for search, (5) output file path");
        else {
            HttpService node = new HttpService(args[0]);
            web3 = Web3j.build(node);

            crab = args[1];
            ethusdcPool = args[2];
            startBlockSearch = new DefaultBlockParameterNumber(Long.parseLong(args[3]));

            if(args[4].isBlank() || Long.parseLong(args[4]) == 0 || Long.parseLong(args[4]) < Long.parseLong(args[3]))
                filter = new EthFilter(startBlockSearch, DefaultBlockParameterName.LATEST, crab).addOptionalTopics("0x878fd3ca52ad322c7535f559ee7c91afc67363073783360ef1b1420589dc6174", "0x4c1a959210172325f5c6678421c3834b04ae8ce57f7a7c0c0bbfbb62bca37e34", "0xa13b272c1cf13ba724064d3d4809d9f557aab8da2bb582cba031a2f57e728e9d", "0x5d85169ff8329e90f3225f9798e0eba54d00c55d3bbfe201a0d1606febb23a8e");
            else
                filter = new EthFilter(startBlockSearch, new DefaultBlockParameterNumber(Long.parseLong(args[4])), crab).addOptionalTopics("0x878fd3ca52ad322c7535f559ee7c91afc67363073783360ef1b1420589dc6174", "0x4c1a959210172325f5c6678421c3834b04ae8ce57f7a7c0c0bbfbb62bca37e34", "0xa13b272c1cf13ba724064d3d4809d9f557aab8da2bb582cba031a2f57e728e9d", "0x5d85169ff8329e90f3225f9798e0eba54d00c55d3bbfe201a0d1606febb23a8e");

            callAndSetOtherContracts();

            outputFile = new PrintWriter(args[5]);

            StringBuilder sb = new StringBuilder();
            sb.append("Timestamp").append(",").append("ETH Price").append(",").append("USD Price").append(",").append("Hedged?").append('\n');

            outputFile.print(sb);

            outputFile.flush();

            web3.ethLogFlowable(filter).subscribe(
                    log -> {
                        System.out.println("\nBlock " + log.getBlockNumber().toString());

                        if(lastBlock.equals(log.getBlockNumber())) {
                            return;
                        }

                        lastBlock = log.getBlockNumber();

                        EthBlock block = web3.ethGetBlockByNumber(new DefaultBlockParameterNumber(log.getBlockNumber()), false).send();
                        boolean hedged = false;

                        for(String s: log.getTopics()) {
                            if(s.equalsIgnoreCase("0x4c1a959210172325f5c6678421c3834b04ae8ce57f7a7c0c0bbfbb62bca37e34") || s.equalsIgnoreCase("0x878fd3ca52ad322c7535f559ee7c91afc67363073783360ef1b1420589dc6174")) {
                                hedged = true;
                                break;
                            }
                        }

                        try {
                            Double[] prices = calculatePriceAtBlock(log.getBlockNumber(), hedged);
                            writeToFile(block.getBlock().getTimestamp().doubleValue(), prices[0], prices[1], hedged);
                        } catch (Exception e) {
                            System.out.println(e.getMessage());
                        }

                    }
            );
        }
    }

    public static void writeToFile(Double block, Double ethPrice, Double usdPrice, boolean hedged) {
        StringBuilder sb = new StringBuilder();
        sb.append(block).append(',').append(ethPrice).append(',').append(usdPrice).append(',').append(hedged).append('\n');

        outputFile.print(sb);

        outputFile.flush();
    }

    public static void callAndSetOtherContracts() throws ExecutionException, InterruptedException {
        Function callCrabOracle = new Function("oracle",
                Collections.emptyList(),
                Arrays.asList(
                        new TypeReference<Address>() { }
                )
        );
        Function callwPowerPerp = new Function("wPowerPerp",
                Collections.emptyList(),
                Arrays.asList(
                        new TypeReference<Address>() { }
                )
        );
        Function callethWSqueethPool = new Function("ethWSqueethPool",
                Collections.emptyList(),
                Arrays.asList(
                        new TypeReference<Address>() { }
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

    public static Double[] calculatePriceAtBlock(BigInteger block, boolean hedged) throws ExecutionException, InterruptedException {
        BigInteger calculatedPrice, shortoSQTH, ethCollateral, priceOfoSQTH, priceOfETHinUSD, ethTerms, totalSupply;

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
                        new Uint32(420),
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
                        new Uint32(420),
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

        ethCollateral = (BigInteger)  decodedList_0.get(2).getValue();
        shortoSQTH = (BigInteger) decodedList_0.get(3).getValue();
        priceOfoSQTH = (BigInteger) decodedList_1.get(0).getValue();
        priceOfETHinUSD = (BigInteger) decodedList_2.get(0).getValue();
        totalSupply = (BigInteger) decodedList_3.get(0).getValue();

//        System.out.println(ethCollateral.doubleValue());
//        System.out.println(shortoSQTH.doubleValue());
//        System.out.println(priceOfoSQTH.doubleValue());
//        System.out.println(priceOfETHinUSD.doubleValue());

        // 2+2 is 4, minus 1 that's 3 quick maths
        ethTerms = ethCollateral.subtract(shortoSQTH.multiply(priceOfoSQTH).divide(BigInteger.valueOf((long) Math.pow(10,18))));
        calculatedPrice = ethTerms.multiply(priceOfETHinUSD).divide(BigInteger.valueOf((long) Math.pow(10,18)));

//        System.out.println("ETH Terms: " + ethTerms.multiply(BigInteger.valueOf((long) Math.pow(10,18))).divide(totalSupply).doubleValue() / Math.pow(10, 18));

        return new Double[] {
            ethTerms.multiply(BigInteger.valueOf((long) Math.pow(10,18))).divide(totalSupply).doubleValue() / Math.pow(10, 18),
            calculatedPrice.multiply(BigInteger.valueOf((long) Math.pow(10,18))).divide(totalSupply).doubleValue() / Math.pow(10, 18)
        };
    }
}
