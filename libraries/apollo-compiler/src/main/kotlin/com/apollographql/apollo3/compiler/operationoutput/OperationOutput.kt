package com.apollographql.apollo3.compiler.operationoutput

import kotlinx.serialization.Serializable

/**
 * [OperationOutput] is a map where the operationId is the key and [OperationDescriptor] the value
 *
 * By default the operationId is a sha256 but it can be changed for custom whitelisting implementations
 */
typealias OperationOutput = Map<String, OperationDescriptor>

/**
 * This structure is also generated by other tools (iOS, cli, ...), try to keep the field names if possible.
 */
@Serializable
class OperationDescriptor(
    val name: String,
    val source: String,
    val type: String
)

fun OperationOutput.findOperationId(name: String): String {
  val id = entries.find { it.value.name == name }?.key
  check(id != null) {
    "cannot find operation ID for '$name', check your operationOutput.json"
  }
  return id
}

