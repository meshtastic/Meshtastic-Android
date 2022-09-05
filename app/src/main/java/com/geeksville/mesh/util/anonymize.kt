package com.geeksville.mesh.util

/**
 * When printing strings to logs sometimes we want to print useful debugging information about users
 * or positions.  But we don't want to leak things like usernames or locations.  So this function
 * if given a string, will return a string which is a maximum of three characters long, taken from the tail
 * of the string.  Which should effectively hide real usernames and locations,
 * but still let us see if values were zero, empty or different.
 */
val Any?.anonymize: String
    get() = this.anonymize()

/**
 * A version of anonymize that allows passing in a custom minimum length
 */
fun Any?.anonymize(maxLen: Int = 3) =
    if (this != null) ("..." + this.toString().takeLast(maxLen)) else "null"