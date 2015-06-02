package utils

import org.mindrot.jbcrypt.BCrypt

object CryptoUtil {

  /**
   * Encrypts a string (i.e. a password) using a version of Blowfish block cipher
   * @param string String to be encrypted
   * @return
   */
  def encrypt(string:String):String = {
    BCrypt.hashpw(string, BCrypt.gensalt(12))
  }

  /**
   * Checks that an unencrypted string matches one that has previously been encrypted
   * @param string Unencrypted string to be checked
   * @param encrypted Encrypted string to be checked against unencrypted one
   * @return true if they match, false otherwise
   */
  def check(string:String, encrypted:String):Boolean = {
    BCrypt.checkpw(string, encrypted)
  }

}
