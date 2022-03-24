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
import java.util.concurrent.ExecutionException;

public class Main {

    public static final String zeroAddress = "0x0000000000000000000000000000000000000000";

    public static PrintWriter outputFile;
    public static Web3j web3;
    public static EthFilter filter;
    public static String crab, oSQTH, controller, pool, oracle, ethusdcPool, usdc = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48", weth = "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2";

    public static void main(String[] args) throws Exception {
        if(args.length != 7)
             throw new Exception("(0) node HTTP addr, (1) crab address, (2) oSQTH, (3) controller, (4) oSQTH/WETH pool, (5) oracle, (6) ethusdc pool");
        else {
            HttpService node = new HttpService(args[0]);
            web3 = Web3j.build(node);

            crab = args[1];
            oSQTH = args[2];
            controller = args[3];
            pool = args[4];
            oracle = args[5];
            ethusdcPool = args[6];

            filter = new EthFilter(new DefaultBlockParameterNumber(14048622), DefaultBlockParameterName.LATEST, crab).addOptionalTopics("0xa13b272c1cf13ba724064d3d4809d9f557aab8da2bb582cba031a2f57e728e9d", "0x5d85169ff8329e90f3225f9798e0eba54d00c55d3bbfe201a0d1606febb23a8e");

            outputFile = new PrintWriter("./output/data.csv");

            StringBuilder sb = new StringBuilder();
            sb.append("Timestamp").append(",").append("ETH Price").append(",").append("USD Price").append('\n');

            outputFile.print(sb);

            outputFile.flush();

            web3.ethLogFlowable(filter).subscribe(
                    log -> {
                        System.out.println("\nBlock " + log.getBlockNumber().toString());

                        EthBlock block = web3.ethGetBlockByNumber(new DefaultBlockParameterNumber(log.getBlockNumber()), false).send();

                        try {
                            Double[] prices = calculatePriceAtBlock(log.getBlockNumber());
                            writeToFile(block.getBlock().getTimestamp().doubleValue(), prices[0], prices[1]);
                        } catch (Exception e) {
                            System.out.println(e.getMessage());
                        }

                    }
            );
        }
    }

    public static void writeToFile(Double block, Double ethPrice, Double usdPrice) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(block).append(',').append(ethPrice).append(',').append(usdPrice).append('\n');

        outputFile.print(sb);

        outputFile.flush();
    }

    public static Double[] calculatePriceAtBlock(BigInteger block) throws ExecutionException, InterruptedException {
        BigInteger calculatedPrice, shortoSQTH, ethCollateral, priceOfoSQTH, priceOfETHinUSD, ethTerms, totalSupply;

        // Functions to call

        Function callVaultsFunc = new Function("vaults",
                Arrays.asList(
                    new org.web3j.abi.datatypes.Uint(BigInteger.valueOf(70))
                ),
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
        Function callTotalSupply = new Function("totalSupply", Collections.emptyList(), Arrays.asList(new TypeReference<Uint256>() { }));

        // Call
        EthCall response_vaultsFunc = web3.ethCall(
                Transaction.createEthCallTransaction(
                        zeroAddress,
                        controller,
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