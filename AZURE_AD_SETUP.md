# Azure AD OAuth2 Setup Guide

This guide walks through setting up Azure AD authentication for LuxBack in production environments.

## Overview

LuxBack uses Azure AD OAuth2 for authentication in GCP environments (int-gcp, pre-prod-gcp, prod-gcp). Users are authenticated through Azure AD, and their group memberships determine their application roles (USER or ADMIN).

## Prerequisites

- Azure AD tenant with admin access
- Two security groups created:
   - One for admin users
   - One for regular users
- Permissions to create app registrations
- Access to create and manage security groups

## Step 1: Register Application in Azure AD

### 1.1 Create New App Registration

1. Navigate to **Azure Portal** → **Azure Active Directory** → **App registrations**

2. Click **New registration**

**Configuration:**
- **Name**: `LuxBack` (or `LuxBack-Int`, `LuxBack-Prod` for separate registrations)
- **Supported account types**: `Accounts in this organizational directory only (Single tenant)`
- **Redirect URI**:
   - Type: `Web`
   - URL format: `https://your-app-url.com/login/oauth2/code/azure`

**Redirect URIs by environment:**
- Integration: `https://luxback-int-abc123.run.app/login/oauth2/code/azure`
- Pre-prod: `https://luxback-preprod-def456.run.app/login/oauth2/code/azure`
- Production: `https://luxback-prod-ghi789.run.app/login/oauth2/code/azure`

**Important:** The redirect URI must match exactly, including the path `/login/oauth2/code/azure`.

3. Click **Register**

4. **Copy the following values** (you'll need them later):
   - Application (client) ID
   - Directory (tenant) ID

## Step 2: Configure Application

### 2.1 Create Client Secret

1. In your app registration, go to **Certificates & secrets**

2. Click **New client secret**
   - **Description**: `LuxBack Client Secret` (or add environment name)
   - **Expires**: 12 months (recommended) or 24 months

3. Click **Add**

4. **⚠️ CRITICAL:** Copy the **Value** immediately. You won't be able to see it again.
   - Store this securely (e.g., in a password manager)
   - This is your `AZURE_CLIENT_SECRET`

**Best Practices:**
- Set calendar reminder to rotate secrets before expiry
- Use separate secrets for each environment
- Never commit secrets to source control

### 2.2 Configure API Permissions

1. Go to **API permissions**

2. Click **Add a permission**

3. Select **Microsoft Graph**

4. Choose **Delegated permissions**

5. Add these permissions:
   - `openid` - Required for OpenID Connect
   - `profile` - User profile information
   - `email` - User email address
   - `User.Read` - Read user profile
   - `GroupMember.Read.All` - Read group memberships (critical for role mapping)

6. Click **Add permissions**

7. Click **Grant admin consent for [Your Organization]**
   - This requires admin privileges
   - All users in your organization will be able to use the application

**Expected result:**
- All permissions show "Granted for [Your Organization]"
- Green checkmarks next to each permission

### 2.3 Configure Token Configuration

This is critical for role mapping to work correctly.

1. Go to **Token configuration**

2. Click **Add groups claim**

3. Configuration:
   - **Select group types to include in tokens:**
      - ☑ **Security groups**

   - **Customize token properties by type:**
      - ID: ☑ **Group ID**
      - Access: ☑ **Group ID**

   - **Filter groups:**
      - Select: **Groups assigned to the application**

4. Click **Add**

**Why this matters:**
- The application uses group IDs to map users to roles
- Without this configuration, users won't be assigned any roles
- The "Group ID" option provides stable identifiers

### 2.4 Enable ID Tokens

1. Go to **Authentication**

2. Under **Implicit grant and hybrid flows**, check:
   - ☑ **ID tokens** (used for implicit and hybrid flows)

3. Verify **Redirect URIs** are correct:
   - Should match your Cloud Run service URL
   - Must end with `/login/oauth2/code/azure`

4. Click **Save**

## Step 3: Create Security Groups

Security groups determine which users can access the application and what roles they have.

### 3.1 Create Admin Group

1. Navigate to **Azure Active Directory** → **Groups**

2. Click **New group**

3. Configuration:
   - **Group type**: `Security`
   - **Group name**: `LuxBack Admins` (or `LuxBack-Prod-Admins`)
   - **Group description**: `Administrators for LuxBack application - can upload, browse, and download files`
   - **Membership type**: `Assigned`
   - **Members**: Add admin users (you can add more later)

4. Click **Create**

5. **⚠️ Copy the Object ID** from the group's Overview page
   - This is your `AZURE_ADMIN_GROUP_ID`
   - Format: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`

### 3.2 Create User Group

1. Click **New group** again

2. Configuration:
   - **Group type**: `Security`
   - **Group name**: `LuxBack Users` (or `LuxBack-Prod-Users`)
   - **Group description**: `Regular users for LuxBack application - can upload files`
   - **Membership type**: `Assigned`
   - **Members**: Add regular users

3. Click **Create**

4. **⚠️ Copy the Object ID**
   - This is your `AZURE_USER_GROUP_ID`

**Important Notes:**
- Users in the Admin group automatically get USER role too
- Users must be in at least one group to access the application
- You can add/remove users from groups at any time
- Changes take effect on next login

### 3.3 Assign Groups to Application (Optional but Recommended)

This step makes the application visible in users' "My Apps" portal.

1. Go to **Enterprise applications** in Azure AD

2. Find your application: `LuxBack`

3. Go to **Users and groups**

4. Click **Add user/group**

5. Add both security groups:
   - `LuxBack Admins`
   - `LuxBack Users`

6. Assign appropriate roles (or leave as default)

## Step 4: Collect Required Values

You'll need these values for environment configuration:

| Variable | Where to Find It | Example |
|----------|------------------|---------|
| `AZURE_CLIENT_ID` | App registration → Overview → Application (client) ID | `a1b2c3d4-...` |
| `AZURE_CLIENT_SECRET` | Certificates & secrets → Client secrets → Value | `xyz789...` |
| `AZURE_TENANT_ID` | App registration → Overview → Directory (tenant) ID | `e5f6g7h8-...` |
| `AZURE_ADMIN_GROUP_ID` | Groups → LuxBack Admins → Object ID | `i9j0k1l2-...` |
| `AZURE_USER_GROUP_ID` | Groups → LuxBack Users → Object ID | `m3n4o5p6-...` |

**Security checklist:**
- [ ] Client secret copied and stored securely
- [ ] Client secret expiration date noted
- [ ] All group Object IDs copied
- [ ] Values stored in secure location (not in source control)

## Step 5: Configure Environment Variables

### For CloudRun Deployment

Set these environment variables in your CloudRun service:

```bash
# Azure AD Configuration
AZURE_CLIENT_ID=your-client-id-here
AZURE_CLIENT_SECRET=your-client-secret-here
AZURE_TENANT_ID=your-tenant-id-here
AZURE_ADMIN_GROUP_ID=your-admin-group-object-id
AZURE_USER_GROUP_ID=your-user-group-object-id

# Spring Profile
SPRING_PROFILES_ACTIVE=prod-gcp
```

**Deployment command example:**
```bash
gcloud run deploy luxback-prod \
  --set-env-vars SPRING_PROFILES_ACTIVE=prod-gcp \
  --set-env-vars AZURE_CLIENT_ID=$AZURE_CLIENT_ID \
  --set-env-vars AZURE_CLIENT_SECRET=$AZURE_CLIENT_SECRET \
  --set-env-vars AZURE_TENANT_ID=$AZURE_TENANT_ID \
  --set-env-vars AZURE_ADMIN_GROUP_ID=$AZURE_ADMIN_GROUP_ID \
  --set-env-vars AZURE_USER_GROUP_ID=$AZURE_USER_GROUP_ID \
  --region us-central1
```

### For Local Testing with Azure AD

**Option 1: Environment variables**
```bash
export AZURE_CLIENT_ID="your-client-id-here"
export AZURE_CLIENT_SECRET="your-client-secret-here"
export AZURE_TENANT_ID="your-tenant-id-here"
export AZURE_ADMIN_GROUP_ID="your-admin-group-object-id"
export AZURE_USER_GROUP_ID="your-user-group-object-id"
export SPRING_PROFILES_ACTIVE="int-gcp"

mvn spring-boot:run
```

**Option 2: IDE configuration**

In IntelliJ IDEA or Eclipse:
1. Create run configuration for Spring Boot
2. Add environment variables
3. Set active profile to `int-gcp`

**Note:** For local testing, use the integration environment credentials and redirect URI must be `http://localhost:8080/login/oauth2/code/azure`.

## Step 6: Verify Setup

### 6.1 Test Authentication Flow

1. Start the application (locally or in CloudRun)

2. Navigate to the application URL

3. **Expected behavior:**
   - Redirected to Azure AD login page
   - Sign in with credentials
   - Consent screen (first time only)
   - Redirected back to application
   - Successfully logged in

4. Check application logs:
   ```
   OAuth2 Authorities Mapper initialized
   Mapping authorities for user: name=John Doe, email=john.doe@example.com
   User groups: [xxxx-xxxx-xxxx-xxxx, yyyy-yyyy-yyyy-yyyy]
   Granted ADMIN role to user: john.doe@example.com
   Granted USER role to user: john.doe@example.com
   ```

### 6.2 Verify Role Assignment

**Test as regular user:**
1. Log in with user account (in Users group)
2. Should see upload page
3. Should NOT see "Files" link in navbar
4. Accessing `/files` should show "Access Denied"

**Test as admin:**
1. Log in with admin account (in Admins group)
2. Should see upload page
3. Should see "Files" link in navbar
4. Should be able to access `/files` page
5. Should be able to download files

### 6.3 Test Group Membership

**Add user to group:**
1. In Azure AD, add user to `LuxBack Users` group
2. User logs out and logs in again
3. Should now have access to the application

**Remove user from groups:**
1. Remove user from all LuxBack groups
2. User logs out and logs in again
3. Should get "Access Denied" or see no navigation options

## Troubleshooting

### Users Can't Log In

**Symptom:** Authentication fails or redirects to error page

**Possible causes:**

1. **User not in any group**
   - **Solution:** Add user to `LuxBack Users` or `LuxBack Admins` group
   - Verify group membership in Azure AD
   - User must log out and log in again

2. **Admin consent not granted**
   - **Solution:** Go to API permissions → Grant admin consent
   - Verify all permissions show "Granted for [Organization]"

3. **Incorrect redirect URI**
   - **Solution:** Verify redirect URI exactly matches
   - Check for typos, trailing slashes, http vs https
   - Format: `https://your-domain/login/oauth2/code/azure`

### No Roles Assigned After Login

**Symptom:** User logs in but has no access, logs show "No roles assigned"

**Possible causes:**

1. **Groups claim missing from token**
   - **Solution:** Check Token configuration
   - Ensure "Security groups" is selected
   - Ensure "Groups assigned to the application" is selected
   - Re-deploy after changes

2. **Incorrect group IDs in configuration**
   - **Solution:** Verify `AZURE_ADMIN_GROUP_ID` and `AZURE_USER_GROUP_ID`
   - Group IDs must be Object IDs from Azure AD
   - Format: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`

3. **GroupMember.Read.All permission not granted**
   - **Solution:** Check API permissions
   - Ensure admin consent is granted
   - Permission must show as "Granted for [Organization]"

### Error: "AADSTS50011: Reply URL mismatch"

**Symptom:** Error during login with AADSTS50011

**Solution:**
1. Check redirect URI in app registration
2. Verify it exactly matches: `https://your-domain/login/oauth2/code/azure`
3. No trailing slashes
4. Correct protocol (https in production, http for localhost)
5. After changing, wait 5 minutes for Azure AD to propagate

### Groups Claim Not in Token

**Symptom:** Logs show "No 'groups' claim found in token"

**Solution:**
1. Go to **Token configuration** in app registration
2. Verify "groups" claim is configured
3. Select "Security groups" and "Group ID"
4. Ensure "Groups assigned to the application" is selected
5. Grant admin consent if needed
6. Have user log out and log in again

### GroupMember.Read.All Permission Issues

**Symptom:** Error about insufficient permissions to read groups

**Solution:**
1. Go to **API permissions**
2. Verify `GroupMember.Read.All` is present
3. Type should be "Delegated"
4. Click "Grant admin consent"
5. Verify status shows "Granted for [Organization]"

### Too Many Groups (Token Size Issue)

**Symptom:** Users in many groups experience login issues

**Solution:**
- Azure AD limits token size
- If user is in >200 groups, token may be truncated
- Use "Groups assigned to the application" filter
- Consider using App Roles instead of groups (advanced)

## Security Best Practices

### 1. Client Secret Management

**Rotation:**
- Rotate secrets every 6-12 months
- Set calendar reminders before expiration
- Use overlapping validity periods during rotation

**Storage:**
- Use Google Secret Manager for production
- Never commit to source control
- Use separate secrets per environment

**Monitoring:**
- Set up alerts for secret expiration
- Monitor failed authentication attempts
- Review audit logs regularly

### 2. Group Management

**Regular reviews:**
- Quarterly review of group memberships
- Remove users who no longer need access
- Verify admin group membership is current

**Principle of least privilege:**
- Only grant admin role when necessary
- Start users with USER role
- Promote to ADMIN only when needed

### 3. Conditional Access

**Consider implementing:**
- Multi-factor authentication (MFA) requirement
- Trusted location requirements
- Device compliance requirements
- Sign-in risk policies

**Azure AD setup:**
1. Navigate to **Security** → **Conditional Access**
2. Create new policy
3. Assign to LuxBack application
4. Configure required conditions

### 4. Audit Logging

**Monitor:**
- Azure AD sign-in logs
- Application audit logs (CSV files)
- Failed authentication attempts
- Group membership changes

**Azure AD logs:**
- Navigate to **Monitoring** → **Sign-in logs**
- Filter by application: LuxBack
- Review for suspicious activity

## Advanced Configuration

### Using App Roles Instead of Groups

For more granular control, you can define app roles:

1. Go to **App registrations** → **App roles**
2. Click **Create app role**
3. Define roles (e.g., "Administrator", "User")
4. Assign users to roles in Enterprise applications
5. Update application code to read from roles claim

**Note:** This requires code changes to `OAuth2AuthoritiesMapper`.

### Multi-Tenant Configuration

To support multiple tenants:

1. Change **Supported account types** to:
   - "Accounts in any organizational directory (Any Azure AD directory - Multitenant)"

2. Update issuer URI in configuration:
   ```yaml
   spring:
     security:
       oauth2:
         client:
           provider:
             azure:
               issuer-uri: https://login.microsoftonline.com/common/v2.0
   ```

3. Implement tenant-specific logic in application

**Note:** This is advanced and not recommended unless required.

## Testing Checklist

Use this checklist to verify your Azure AD setup:

**App Registration:**
- [ ] App registered with correct name
- [ ] Redirect URI configured correctly
- [ ] Client secret created and securely stored
- [ ] Client ID and Tenant ID copied

**API Permissions:**
- [ ] openid permission added
- [ ] profile permission added
- [ ] email permission added
- [ ] User.Read permission added
- [ ] GroupMember.Read.All permission added
- [ ] Admin consent granted for all permissions
- [ ] All permissions show "Granted for [Organization]"

**Token Configuration:**
- [ ] Groups claim added
- [ ] "Security groups" selected
- [ ] "Group ID" selected for both ID and Access tokens
- [ ] "Groups assigned to the application" selected

**Authentication Settings:**
- [ ] ID tokens enabled
- [ ] Redirect URIs verified

**Security Groups:**
- [ ] Admin group created with Object ID copied
- [ ] User group created with Object ID copied
- [ ] Test users added to appropriate groups
- [ ] Groups assigned to application (optional)

**Environment Configuration:**
- [ ] AZURE_CLIENT_ID set
- [ ] AZURE_CLIENT_SECRET set
- [ ] AZURE_TENANT_ID set
- [ ] AZURE_ADMIN_GROUP_ID set
- [ ] AZURE_USER_GROUP_ID set
- [ ] SPRING_PROFILES_ACTIVE set to appropriate profile

**Testing:**
- [ ] Regular user can log in and access upload page
- [ ] Regular user cannot access admin pages
- [ ] Admin user can log in and access all pages
- [ ] Admin user can download files
- [ ] Application logs show correct role assignment
- [ ] Logout works correctly

## References

- [Microsoft identity platform documentation](https://docs.microsoft.com/en-us/azure/active-directory/develop/)
- [Spring Security OAuth2 documentation](https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html)
- [Azure AD app registration guide](https://docs.microsoft.com/en-us/azure/active-directory/develop/quickstart-register-app)
- [Azure AD groups and roles](https://docs.microsoft.com/en-us/azure/active-directory/develop/howto-add-app-roles-in-azure-ad-apps)

## Support

If you encounter issues not covered in this guide:

1. Check application logs for detailed error messages
2. Verify all configuration values are correct
3. Review Azure AD audit logs
4. Ensure all prerequisites are met
5. Try the troubleshooting steps above

---

**Last Updated:** November 2024  
**Status:** Production-ready  
**Version:** 0.0.1-SNAPSHOT