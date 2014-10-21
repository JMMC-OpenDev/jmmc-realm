/*******************************************************************************
 * JMMC project ( http://www.jmmc.fr ) - Copyright (C) CNRS.
 ******************************************************************************/
package org.exist.security.realm.jmmc;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.exist.EXistException;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.security.AbstractAccount;
import org.exist.security.AbstractRealm;
import org.exist.security.Account;
import org.exist.security.AuthenticationException;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.internal.SubjectAccreditedImpl;
import org.exist.storage.DBBroker;
import org.apache.log4j.Logger;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.security.AXSchemaType;
import org.exist.security.internal.SecurityManagerImpl;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.w3c.dom.Document;
import org.w3c.dom.Node;


/**
 * An eXist-db realm for authentication with the JMMC user database.
 *
 * It creates a new eXist-db account with the registration information from
 * the JMMC service. Accounts are created on-demand. The user password is
 * never stored in eXist-db, user identification is still performed by the
 * underlying JMMC user database.
 *
 * All authenticated users are added to a 'users' group. Users with specific
 * credentials are also added to groups named after the credential.
 *
 * To use it:
 * <ul>
 *   <li>
 *     place the JAR file in the lib/core directory of eXist-db
 *   </li>
 *   <li>
 *     edit the configuration fo the Security Manager (/db/system/security/config.xml)
 *     and add:
 *     <pre>
 *     {@code
 *     <realm id="JMMC">
 *       <url>https://jmmc.obs.ujf-grenoble.fr/account/manage.php</url>
 *     </realm>
 *     }
 *     </pre>
 *   </li>
 * </ul>
 *
 * @author Patrick Bernaud
 */
@ConfigurationClass("realm")
public class JMMCRealm extends AbstractRealm {

    private final static Logger LOG = Logger.getLogger(JMMCRealm.class);

    @ConfigurationFieldAsAttribute("id")
    public static String ID = "JMMC";

    @ConfigurationFieldAsElement("url")
    protected String url = null;

    public JMMCRealm(SecurityManagerImpl sm, Configuration config) {
        super(sm, config);

        configuration = Configurator.configure(this, config);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public Subject authenticate(String accountName, Object credentials) throws AuthenticationException {
        final boolean checked;

        try {
            checked = checkPassword(accountName, (String) credentials);
        } catch (Exception ex) {
            LOG.debug(ex.getMessage(), ex);
            LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ex.getMessage()));
            return null;
        }

        if (!checked) {
            throw new AuthenticationException(AuthenticationException.WRONG_PASSWORD, "Bad password for user '" + accountName + "'");
        }

        final AbstractAccount account = (AbstractAccount) getAccount(accountName);
        if (account != null) {
            return new SubjectAccreditedImpl(account, true);
        } else {
            throw new AuthenticationException(AuthenticationException.ACCOUNT_NOT_FOUND, "Account '" + accountName + "' can not be found.");
        }
    }

    /**
     * Check a user password with the JMMC authentication service.
     *
     * @param user the JMMC user name
     * @param password the user password
     *
     * @return true if user exists and password is correct, false otherwise
     * 
     * @throws Exception if the request failed
     */
    private boolean checkPassword(final String user, final String password) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("action", "checkPassword");
        params.put("email", user);
        params.put("password", password);

        InputStream is = httpPost(params);

        Document doc = parseXML(is);
        return doc.getElementsByTagName("true").item(0) != null;
    }

    /**
     * An anonymous class to store the JMMC user details from an XML response of the service
     */
    class User {
        String email;

        String fullName;

        String affiliation;

        final List<String> credentials = new ArrayList<>();

        public User(String email, Node data) {
            this.email = email;

            // browse the XML response and class set members
            for (Node child = data.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                switch (child.getNodeName()) {
                    case "name":
                        this.fullName = child.getTextContent();
                        break;
                    case "credential":
                        this.credentials.add(child.getFirstChild().getNodeName());
                        break;
                    case "affiliation":
                        this.affiliation = child.getTextContent();
                        break;
                    default:
                }
            }

        }
    }

    /**
     * Retrieve user info from the JMMC authentication service.
     *
     * @param user the JMMC user name
     *
     * @return a user description or null if user is unknown
     * 
     * @throws Exception if the request failed
     */
    private User getInfo(final String user) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("action", "getInfo");
        params.put("email", user);

        InputStream is = httpPost(params);

        Document doc = parseXML(is);
        Node response = doc.getElementsByTagName("response").item(0);
        if (response == null) {
            return null;
        }
        
        return new User(user, response);
    }

    /**
     * Perform a HTTP POST request to the JMMC authentication service.
     *
     * It sends the specified data in the request in the same way that a
     * browser would do from a HTML form filled by an user. Each form field
     * is built from a key and a value of the given map.
     *
     * @param stream the input stream
     *
     * @return the response to the request
     * 
     * @throws Exception if the request failed
     */
    private InputStream httpPost(Map<String, String> params) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(this.url).openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setUseCaches(false);
        connection.setDoInput(true);
        connection.setDoOutput(true);

        String urlParameters = "";
        for (Map.Entry<String, String> entry : params.entrySet()) {
            urlParameters += entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), "UTF-8") + "&";
        }

        connection.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));

        try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
            wr.writeBytes(urlParameters);
            wr.flush();
        }

        return connection.getInputStream();
    }

    /**
     * Parse an input stream as an XML document.
     *
     * @param stream the input stream
     *
     * @return the XML document
     * 
     * @throws Exception if IO or parse error occur
     */
    private static Document parseXML(InputStream stream) throws Exception {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

        return documentBuilder.parse(stream);
    }

    @Override
    public final synchronized Account getAccount(final String name) {
        // try to get the cached account first
        final Account acct = super.getAccount(name);
        if (acct != null) {
            return acct;
        } else {
            try {
                final User user = getInfo(name);
                return createAccount(user);
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
                return null;
            }
        }
    }

    @Override
    public final Group getGroup(String name) {
        Group group = super.getGroup(name);
        if (group != null) {
            return group;
        }
        
        // unknown group, create a new one
        try {
            return createGroup(name);
        } catch(AuthenticationException ex) {
            LOG.error(ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * Create a new account in eXist-db for a JMMC user.
     *
     * @param user the JMMC user details
     *
     * @return an eXist-db account for the JMMC user
     *
     * @throws Exception if it failed to create the account
     */
    private Account createAccount(final User user) throws Exception {
        LOG.debug("creating account " + user.email);
        return executeAsSystemUser(new Unit<Account>() {
            @Override
            public Account execute(DBBroker broker) throws EXistException, PermissionDeniedException {
                final UserAider userAider = new UserAider(ID, user.email, getSecurityManager().getGroup("guest"));

                for (String credential : user.credentials) {
                    userAider.addGroup(getSecurityManager().getGroup(credential));
                }

                if (user.fullName != null) {
                    userAider.setMetadataValue(AXSchemaType.FULLNAME, user.fullName);
                }

                if (user.affiliation != null) {
                    // not queryable from the security manager as regular metadata item
                    userAider.setMetadataValue(JMMCSchemaType.AFFILIATION, user.affiliation);
                }

                final Account account = getSecurityManager().addAccount(userAider);
                
                return account;
            }
        });
    }

    /**
     * Create new new group in eXist-db for a JMMC credential.
     *
     * @param groupname the new of the new group
     *
     * @return an eXist-db group of the given name
     *
     * @throws AuthenticationException if it failed to create the group
     */
    private Group createGroup(final String groupname) throws AuthenticationException {
        LOG.debug("creating group " + groupname);
        try {
            return getSecurityManager().addGroup(new GroupAider(ID, groupname));
        } catch (PermissionDeniedException | EXistException e) {
            throw new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteAccount(Account account) throws PermissionDeniedException, EXistException, ConfigurationException {
        return false;
    }

    @Override
    public boolean deleteGroup(Group group) throws PermissionDeniedException, EXistException, ConfigurationException {
        return false;
    }
}
