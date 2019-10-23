package org.ethereum.lists.chains

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import org.kethereum.functions.isValid
import org.kethereum.model.Address
import org.kethereum.rpc.HttpEthereumRPC
import java.io.File
import java.math.BigInteger

val mandatory_fields = listOf(
        "name",
        "shortName",
        "chain",
        "network",
        "chainId",
        "networkId",
        "rpc",
        "faucets",
        "infoURL",
        "nativeCurrency"
)
val optionalFields = listOf(
        "slip44",
        "ens"
)

class FileNameMustMatchChainId : Exception("chainId must match the filename")
class ExtensionMustBeJSON : Exception("filename extension must be json")
class ShouldHaveNoExtraFields(fields: Set<String>) : Exception("should have no extra field $fields")
class ShouldHaveNoMissingFields(fields: Set<String>) : Exception("missing field(s) $fields")
class RPCMustBeList : Exception("rpc must be a list")
class RPCMustBeListOfStrings : Exception("rpc must be a list of strings")
class ENSMustBeObject: Exception("ens must be an object")
class ENSMustHaveOnlyRegistry: Exception("ens can only have a registry currently")
class ENSRegistryAddressMustBeValid: Exception("ens registry must have valid address")

fun checkChain(it: File, connectRPC: Boolean) {
    println("processing $it")
    val jsonObject = Klaxon().parseJsonObject(it.reader())
    val chainAsLong = getNumber(jsonObject, "chainId")

    if (chainAsLong != it.nameWithoutExtension.toLongOrNull()) {
        throw(FileNameMustMatchChainId())
    }

    if (it.extension != "json") {
        throw(ExtensionMustBeJSON())
    }

    getNumber(jsonObject, "networkId")

    val extraFields = jsonObject.map.keys.subtract(mandatory_fields).subtract(optionalFields)
    if (extraFields.isNotEmpty()) {
        throw ShouldHaveNoExtraFields(extraFields)
    }

    val missingFields = mandatory_fields.subtract(jsonObject.map.keys)
    if (missingFields.isNotEmpty()) {
        throw ShouldHaveNoMissingFields(missingFields)
    }

    jsonObject["ens"]?.let {
        if (it !is JsonObject) {
            throw ENSMustBeObject()
        }
        if (it.keys != mutableSetOf("registry")) {
            throw ENSMustHaveOnlyRegistry()
        }

        val address = Address(it["registry"] as String)
        if (!address.isValid()) {
            throw ENSRegistryAddressMustBeValid()
        }
    }

    if (connectRPC) {
        if (jsonObject["rpc"] is List<*>) {
            (jsonObject["rpc"] as List<*>).forEach {
                if (it !is String) {
                    throw(RPCMustBeListOfStrings())
                } else {
                    println("connecting to $it")
                    val ethereumRPC = HttpEthereumRPC(it)
                    println("Client:" + ethereumRPC.clientVersion()?.result)
                    println("BlockNumber:" + ethereumRPC.blockNumber()?.result?.tryBigint())
                    println("GasPrice:" + ethereumRPC.gasPrice()?.result?.tryBigint())
                }
            }
            println()
        } else {
            throw(RPCMustBeList())
        }
    }
}


fun String.tryBigint() = if (startsWith("0x")) {
    try {
        BigInteger(removePrefix("0x"), 16)
    } catch (e: NumberFormatException) {
        null
    }
} else {
    null
}

private fun getNumber(jsonObject: JsonObject, field: String): Long {
    return when (val chainId = jsonObject[field]) {
        is Int -> chainId.toLong()
        is Long -> chainId
        else -> throw(Exception("chain_id must be a number"))
    }
}
