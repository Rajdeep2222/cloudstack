// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.api.command;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;

import javax.inject.Inject;
import javax.naming.NamingException;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.AccountResponse;
import org.apache.cloudstack.api.response.DomainResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.ldap.LdapManager;
import org.apache.cloudstack.ldap.LdapUser;
import org.apache.log4j.Logger;

import com.cloud.user.Account;
import com.cloud.user.AccountService;
import com.cloud.user.UserAccount;

@APICommand(name = "ldapCreateAccount", description = "Creates an account from an LDAP user", responseObject = AccountResponse.class, since = "4.2.0")
public class LdapCreateAccountCmd extends BaseCmd {
    public static final Logger s_logger = Logger
            .getLogger(LdapCreateAccountCmd.class.getName());
    private static final String s_name = "createaccountresponse";

    @Inject
    private LdapManager _ldapManager;

    @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING, description = "Creates the user under the specified account. If no account is specified, the username will be used as the account name.")
    private String accountName;

    @Parameter(name = ApiConstants.ACCOUNT_TYPE, type = CommandType.SHORT, required = true, description = "Type of the account.  Specify 0 for user, 1 for root admin, and 2 for domain admin")
    private Short accountType;

    @Parameter(name = ApiConstants.DOMAIN_ID, type = CommandType.UUID, entityType = DomainResponse.class, description = "Creates the user under the specified domain.")
    private Long domainId;

    @Parameter(name = ApiConstants.TIMEZONE, type = CommandType.STRING, description = "Specifies a timezone for this command. For more information on the timezone parameter, see Time Zone Format.")
    private String timezone;

    @Parameter(name = ApiConstants.USERNAME, type = CommandType.STRING, required = true, description = "Unique username.")
    private String username;

    @Parameter(name = ApiConstants.NETWORK_DOMAIN, type = CommandType.STRING, description = "Network domain for the account's networks")
    private String networkDomain;

    @Parameter(name = ApiConstants.ACCOUNT_DETAILS, type = CommandType.MAP, description = "details for account used to store specific parameters")
    private Map<String, String> details;

    @Parameter(name = ApiConstants.ACCOUNT_ID, type = CommandType.STRING, description = "Account UUID, required for adding account from external provisioning system")
    private String accountUUID;

    @Parameter(name = ApiConstants.USER_ID, type = CommandType.STRING, description = "User UUID, required for adding account from external provisioning system")
    private String userUUID;

    public LdapCreateAccountCmd() {
        super();
    }

    public LdapCreateAccountCmd(final LdapManager ldapManager,
                                final AccountService accountService) {
        super();
        _ldapManager = ldapManager;
        _accountService = accountService;
    }

    UserAccount createCloudstackUserAccount(final LdapUser user) {
        return _accountService.createUserAccount(username, generatePassword(),
                user.getFirstname(), user.getLastname(), user.getEmail(),
                timezone, accountName, accountType, domainId, networkDomain,
                details, accountUUID, userUUID);
    }

    @Override
    public void execute() throws ServerApiException {
        final CallContext callContext = getCurrentContext();
        callContext.setEventDetails("Account Name: " + accountName
                + ", Domain Id:" + domainId);
        try {
            final LdapUser user = _ldapManager.getUser(username);
            validateUser(user);
            final UserAccount userAccount = createCloudstackUserAccount(user);
            if (userAccount != null) {
                final AccountResponse response = _responseGenerator
                        .createUserAccountResponse(userAccount);
                response.setResponseName(getCommandName());
                setResponseObject(response);
            } else {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR,
                        "Failed to create a user account");
            }
        } catch (final NamingException e) {
            throw new ServerApiException(
                    ApiErrorCode.RESOURCE_UNAVAILABLE_ERROR,
                    "No LDAP user exists with the username of " + username);
        }
    }

    private String generatePassword() throws ServerApiException {
        final SecureRandom random = new SecureRandom();
        final int length = 20;
        final String characters = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789!@£$%^&*()_+=";

        String password = "";
        for (int i = 0; i < length; i++) {
            int index = (int) (random.nextDouble() * characters.length());
            password += characters.charAt(index);
        }
        return password;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    CallContext getCurrentContext() {
        return CallContext.current();
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    private boolean validateUser(final LdapUser user) throws ServerApiException {
        if (user.getEmail() == null) {
            throw new ServerApiException(
                    ApiErrorCode.RESOURCE_UNAVAILABLE_ERROR, username
                    + " has no email address set within LDAP");
        }
        if (user.getFirstname() == null) {
            throw new ServerApiException(
                    ApiErrorCode.RESOURCE_UNAVAILABLE_ERROR, username
                    + " has no firstname set within LDAP");
        }
        if (user.getLastname() == null) {
            throw new ServerApiException(
                    ApiErrorCode.RESOURCE_UNAVAILABLE_ERROR, username
                    + " has no lastname set within LDAP");
        }
        return true;
    }
}