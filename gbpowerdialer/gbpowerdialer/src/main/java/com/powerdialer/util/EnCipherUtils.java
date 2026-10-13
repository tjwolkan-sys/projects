package com.powerdialer.util;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EnCipherUtils implements InitializingBean {

  private static final Logger LOG = LoggerFactory.getLogger(EnCipherUtils.class);

  @Value("${campaign.iv.key}")
  private String INIT_IV_PARAM;

  @Value("${campaign.secret.key}")
  private String SECRET_KEY;

  /**
   * Cipher is stored in ThreadLocal because it is not thread safe.
   */
  private ThreadLocal<Cipher> decryptCipherThreadLocal;

  private ThreadLocal<Cipher> encryptCipherThreadLocal;

  @Override
  public void afterPropertiesSet() {
    IvParameterSpec ivParameterSpec = new IvParameterSpec(INIT_IV_PARAM.getBytes(StandardCharsets.UTF_8));
    SecretKeySpec secretKeySpec = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), Constants.SECRET_KEY_ALGORITHM);
    decryptCipherThreadLocal = ThreadLocal.withInitial(() -> createCipher(Cipher.DECRYPT_MODE, ivParameterSpec, secretKeySpec));
    encryptCipherThreadLocal = ThreadLocal.withInitial(() -> createCipher(Cipher.ENCRYPT_MODE, ivParameterSpec, secretKeySpec));
  }

  private Cipher createCipher(int decryptMode, IvParameterSpec ivParameterSpec, SecretKeySpec secretKeySpec) {
    try {
      Cipher cipher = Cipher.getInstance(Constants.CIPHER_ALGORITHM);
      cipher.init(decryptMode, secretKeySpec, ivParameterSpec);
      return cipher;
    } catch (InvalidKeyException | InvalidAlgorithmParameterException | NoSuchPaddingException | NoSuchAlgorithmException e) {
      LOG.error("Can't create cipher with mode " + decryptMode, e);
    }
    return null;
  }

  public String encryptPassword(String decryptedPassword) {
    String encryptedPassword = "";
    try {
      if (decryptedPassword != null)
        encryptedPassword = encrypt(decryptedPassword);
    } catch (Exception ex) {
      LOG.error("An error occurred during encryption", ex);
    }
    return encryptedPassword;
  }

  /**
   * Encrypt the string with this internal algorithm.
   * @param encrypt string object to be encrypt.
   * @return returns encrypted string.
   */
  private String encrypt(String encrypt) throws BadPaddingException, IllegalBlockSizeException {
    byte[] encrypted = encryptCipherThreadLocal.get().doFinal(encrypt.getBytes());
    return Base64.encodeBase64String(encrypted);
  }

  public String decryptPassword(String encryptedPassword) {
    String decryptedPassword = "";
    try {
      if (encryptedPassword != null)
        decryptedPassword = decrypt(encryptedPassword);
    } catch (Exception ex) {
      LOG.error("An error occured during encryption", ex);
    }
    return decryptedPassword;
  }

  /**
   * Decrypt this string with the internal algorithm. The passed argument should
   * be encrypted using {@link #encrypt(String) encrypt} method of this class.
   * @param encrypted encrypted string that was encrypted using
   *          {@link #encrypt(String) encrypt} method.
   * @return decrypted string.
   * @throws BadPaddingException
   * @throws IllegalBlockSizeException
   */
  private String decrypt(String encrypted) throws BadPaddingException, IllegalBlockSizeException {
    byte[] decryptedBytes = decryptCipherThreadLocal.get().doFinal(Base64.decodeBase64(encrypted));
    return new String(decryptedBytes);
  }
}
