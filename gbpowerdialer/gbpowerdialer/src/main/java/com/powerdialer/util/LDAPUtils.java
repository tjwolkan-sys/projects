package com.powerdialer.util;

import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.text.MessageFormat;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.powerdialer.exceptions.LdapBadCredentialsException;
import com.powerdialer.ldap.PasswordRequestControl;

public class LDAPUtils {

  private static final Logger log = LoggerFactory.getLogger(LDAPUtils.class);

  static {
    try {
      PropertiesUtil appProps = new PropertiesUtil("application.properties");
      importCerts(appProps.getProperty("ldap.keystore.path"), appProps.getProperty("ldap.keystore.password"));
    } catch (Exception e) {
      log.error(e.toString());
    }
  }

  private static void importCerts(String keystorePath, String keystorePwd) throws NoSuchAlgorithmException {
    Security.addProvider(SSLContext.getDefault().getProvider());
    // the keystore that holds trusted root certificates
    // NOTE: Replace with path to ssl certificate keystore file below.
    System.setProperty("javax.net.ssl.trustStore", keystorePath);
    // NOTE: Replace with keystore password that was used while converting .cer
    // to .jks file.
    System.setProperty("javax.net.ssl.trustStorePassword", keystorePwd);
    // System.setProperty("javax.net.debug", "all");
    log.info("Keystore certs successfully imported");
  }

  private static DirContext tryConnect(String username, String domain, String pass, String server, String serverFailover, boolean sslEnabled) throws NamingException {
    Hashtable<Object, String> props = new Hashtable<>();
    String principalName = username + domain;

    props.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    props.put(LdapContext.CONTROL_FACTORIES, "com.webphone.ejp.PasswordResponseControlFactory");
    props.put(Context.SECURITY_AUTHENTICATION, "Simple");
    props.put("com.sun.jndi.ldap.connect.timeout", "10000");
    if (sslEnabled) {
      log.info("Using ldaps...");
      props.put(Context.PROVIDER_URL, "ldaps://" + server);
    } else {
      log.info("Using ldap...");
      props.put(Context.PROVIDER_URL, "ldap://" + server);
    }
    LdapContext context;

    try {
      context = new InitialLdapContext(props, null);
      context.addToEnvironment(Context.SECURITY_PRINCIPAL, principalName);
      context.addToEnvironment(Context.SECURITY_CREDENTIALS, pass);
      context.reconnect(new Control[] { new PasswordRequestControl(true) });
    } catch (NamingException e) {
      if (StringUtils.isNotEmpty(serverFailover)) {
        log.warn("Ldap connect error. Trying to failover...");
        return tryConnect(username, domain, pass, serverFailover, null, sslEnabled);
      } else {
        throw e;
      }
    }
    return context;
  }

  public static boolean login(String username, String domain, String pass, String server, String serverFailover, boolean sslEnabled) {
    DirContext ctx;
    try {
      ctx = tryConnect(username, domain, pass, server, serverFailover, sslEnabled);
      ctx.close();
    } catch (NamingException e) {
      log.error(MessageFormat.format("Ldap login failed: {0}", e));
      throw new LdapBadCredentialsException();
    }
    return true;
  }

}
