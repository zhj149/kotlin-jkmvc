package com.jkmvc.db

import java.io.InputStream
import java.io.Reader
import java.sql.*
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaType

/**
 * StringBuilder扩展
 *  删除最后的一段子字符串
 */
public fun StringBuilder.delete(str:String):StringBuilder {
    val start = length - str.length;
    return delete(start, length);
}

/**
 * 首字母大写
 */
public fun String.ucFirst(): String {
    val cs = this.toCharArray()
    if(cs[0] in 'a'..'z')
        cs[0] = cs[0] - 32
    return String(cs)
}

/**
 * 首字母小写
 */
public fun String.lcFirst(): String {
    val cs = this.toCharArray()
    if(cs[0] in 'A'..'Z')
        cs[0] = cs[0] + 32
    return String(cs)
}

/**
 * 匹配方法的名称与参数类型
 */
public fun KFunction<*>.matches(name:String, vararg paramTypes:Class<*>):Boolean{
    // 1 匹配名称
    if(name != this.name)
        return false

    // 2 匹配参数
    // 注： fn.parameters 第一个参数是this，因此比 paramTypes 要多一个参数
    // 2.1 匹配参数个数
    if(paramTypes.size != this.parameters.size - 1)
        return false;

    // 2.2 匹配参数类型
    if(paramTypes != null){
        for (i in paramTypes.indices){
            if(paramTypes[i] != this.parameters[i + 1].type.javaType)
                return false
        }
    }

    return true;
}

/**
 * Connection扩展
 * 执行更新
 */
public fun Connection.execute(sql: String, paras: List<Any?>? = null): Int {
    var pst: PreparedStatement? = null
    var rs: ResultSet? = null;
    try{
        // 准备sql语句
        pst = prepareStatement(sql)
        // 设置参数
        if(paras != null)
            for (i in paras.indices)
                pst.setObject(i + 1, paras[i])
        // 执行
        val rows:Int = pst.executeUpdate()
        // 如果是insert语句，则返回新增id
        if("INSERT.*".toRegex(RegexOption.IGNORE_CASE).matches(sql)){
            rs = pst.getGeneratedKeys(); //获取新增id
            rs.next();
            return rs.getInt(1); //返回新增id
        }
        // 非insert语句返回行数
        return rows;
    }finally{
        rs?.close()
        pst?.close()
    }
}

/**
 * 查询多行
 */
public fun <T> Connection.queryResult(sql: String, paras: List<Any?>? = null, action:(ResultSet) -> T): T {
    var pst: PreparedStatement? = null;
    var rs: ResultSet? = null;
    try {
        // 准备sql语句
        pst = prepareStatement(sql)
        // 设置参数
        if(paras != null)
            for (i in paras.indices)
                pst.setObject(i + 1, paras[i])
        // 查询
        rs = pst.executeQuery()
        // 处理查询结果
        return action(rs);
    } finally {
        rs?.close()
        pst?.close()
    }
}

/**
 * 查询多行
 */
public fun <T> Connection.queryRows(sql: String, paras: List<Any?>? = null, transform:(MutableMap<String, Any?>) -> T): List<T> {
    return queryResult<List<T>>(sql, paras){ rs: ResultSet ->
        // 处理查询结果
        val result = LinkedList<T>()
        rs.forEachRow { row:MutableMap<String, Any?> ->
            result.add(transform(row));// 转换一行数据
        }
        result;
    }
}

/**
 * 查询一行(多列)
 */
public fun <T> Connection.queryRow(sql: String, paras: List<Any?>? = null, transform:(MutableMap<String, Any?>) -> T): T? {
    return queryResult<T?>(sql, paras){ rs: ResultSet ->
        // 处理查询结果
        val row:MutableMap<String, Any?>? = rs.nextRow()
        if(row == null)
            null
        else
            transform(row);// 转换一行数据
    }
}

/**
 * 查询一行一列
 */
public fun Connection.queryCell(sql: String, paras: List<Any?>? = null): Pair<Boolean, Any?> {
    return queryResult<Pair<Boolean, Any?>>(sql, paras){ rs: ResultSet ->
        // 处理查询结果
        rs.nextCell(1)
    }
}

/**
 * 获得结果集的下一行
 */
public inline fun ResultSet.nextRow(): MutableMap<String, Any?>? {
    if(next()) {
        // 获得一行
        val row:MutableMap<String, Any?> = LinkedHashMap<String, Any?>();
        val rsmd = metaData
        for (i in 1..rsmd.columnCount) { // 多列
            val type: Int = rsmd.getColumnType(i); // 类型
            val label: String = rsmd.getColumnLabel(i); // 字段名
            val value: Any? // 字段值
            when (type) {
                Types.CLOB -> value = getClob(i).toString()
                Types.NCLOB -> value = getNClob(i).toString()
                Types.BLOB -> value = getBlob(i).toByteArray()
                else -> value = getObject(i)
            }
            row[label] = value;
        }
        return row
    }

    return null;
}
/**
 * 遍历结果集的每一行
 */
public inline fun ResultSet.forEachRow(action: (MutableMap<String, Any?>) -> Unit): Unit {
    while(true){
        // 获得一行
        val row = nextRow()
        if(row == null)
            break;

        // 处理一行
        action(row)
    }
}

/**
 * 访问结果集的下一行的某列
 */
public inline fun ResultSet.nextCell(i:Int): Pair<Boolean, Any?> {
    val hasNext = next()
    var value: Any? = null; // 字段值
    if(hasNext) {
        // 获得一行的某列
        val rsmd = metaData
        val type: Int = rsmd.getColumnType(i); // 类型
        when (type) { // 字段值
            Types.CLOB -> value = getClob(i).toString()
            Types.NCLOB -> value = getNClob(i).toString()
            Types.BLOB -> value = getBlob(i).toByteArray()
            else -> value = getObject(i)
        }
    }

    return Pair<Boolean, Any?>(hasNext, value);
}

/**
 * 遍历结果集的每一行的某列
 */
public inline fun ResultSet.forEachCell(i:Int, action: (Any?) -> Unit): Unit {
    while(true){
        // 获得一行某列
        val (hasNext, value) = nextCell(i)

        // 处理一行某列
        if(hasNext)
            action(value)
    }
}

/**
 * Blob转ByteArray
 */
public fun Blob?.toByteArray(): ByteArray? {
    if (this == null)
        return null

    var `is`: InputStream? = null
    try {
        `is` = this.binaryStream
        if (`is` == null)
            return null
        val data = ByteArray(this.length().toInt())        // byte[] data = new byte[is.available()];
        if (data.size == 0)
            return null
        `is`.read(data)
        return data
    } finally {
        `is`?.close()

    }
}

/**
 * Clob转String
 */
public fun Clob?.toString(): String? {
    if (this == null)
        return null

    var reader: Reader? = null
    try {
        reader = this.characterStream
        if (reader == null)
            return null
        val buffer = CharArray(this.length().toInt())
        if (buffer.size == 0)
            return null
        reader.read(buffer)
        return String(buffer)
    } finally {
        reader?.close()
    }
}