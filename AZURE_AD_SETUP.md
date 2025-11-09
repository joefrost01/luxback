# Azure AD OAuth2 Setup Guide

This guide walks through setting up Azure AD authentication for LuxBack.

## Prerequisites

- Azure AD tenant with admin access
- Two security groups created:
  - One for admin users
  - One for regular users

## Step 1: Register Application in Azure AD

1. Navigate to **Azure Portal** → **Azure Active Directory** → **App registrations**

2. Click **New registration**
   - **Name**: `LuxBack`
   - **Supported account types**: `Accounts in this organizational directory only (Single tenant)`
   - **Redirect URI**: 
     - Type: `Web`
     - URL: `https://your-app-url.com/login/oauth2/code/azure`
     - For int: `https://luxback-int.run.app/login/oauth2/code/azure`
     - For pre-prod: `https://luxback-preprod.run.app/login/oauth2/code/azure`
     - For prod: `https://luxback-prod.run.app/login/oauth2/code/azure`

3. Click **Register**

## Step 2: Configure Application

### 2.1 Create Client Secret

1. In your app registration, go to **Certificates & secrets**
2. Click **New client secret**
   - **Description**: `LuxBack Client Secret`
   - **Expires**: Choose appropriate duration (recommended: 12 months)
3. Click **Add**
4. **IMPORTANT**: Copy the secret value immediately (you won't be able to see it again)

### 2.2 Configure API Permissions

1. Go to **API permissions**
2. Click **Add a permission**
3. Select **Microsoft Graph**
4. Choose **Delegated permissions**
5. Add these permissions:
   - `openid`
   - `profile`
   - `email`
   - `User.Read`
   - `GroupMember.Read.All` (to read group memberships)
6. Click **Add permissions**
7. Click **Grant admin consent** (requires admin privileges)

### 2.3 Configure Token Configuration

1. Go to **Token configuration**
2. Click **Add groups claim**
3. Select:
   - ☑ **Security groups**
   - Group ID: Choose **Group ID** (not "sAMAccountName")
4. For both ID and Access tokens, select:
   - ☑ **Groups assigned to the application**
5. Click **Add**

### 2.4 Enable ID Tokens

1. Go to **Authentication**
2. Under **Implicit grant and hybrid flows**, check:
   - ☑ **ID tokens** (used for implicit and hybrid flows)
3. Click **Save**

## Step 3: Create Security Groups

1. Navigate to **Azure Active Directory** → **Groups**

2. Create Admin Group:
   - Click **New group**
   - **Group type**: `Security`
   - **Group name**: `LuxBack Admins`
   - **Group description**: `Administrators for LuxBack application`
   - **Members**: Add admin users
   - Click **Create**
   - **Copy the Object ID** (this is your `AZURE_ADMIN_GROUP_ID`)

3. Create User Group:
   - Click **New group**
   - **Group type**: `Security`
   - **Group name**: `LuxBack Users`
   - **Group description**: `Regular users for LuxBack application`
   - **Members**: Add regular users
   - Click **Create**
   - **Copy the Object ID** (this is your `AZURE_USER_GROUP_ID`)

## Step 4: Collect Required Values

You'll need these values for environment configuration:

| Variable | Where to Find It |
|----------|------------------|
| `AZURE_CLIENT_ID` | App registration → Overview → Application (client) ID |
| `AZURE_CLIENT_SECRET` | Certificates & secrets → Client secrets → Value (copied earlier) |
| `AZURE_TENANT_ID` | App registration → Overview → Directory (tenant) ID |
| `AZURE_ADMIN_GROUP_ID` | Groups → LuxBack Admins → Object ID |
| `AZURE_USER_GROUP_ID` | Groups → LuxBack Users → Object ID |

## Step 5: Configure Environment Variables

### For CloudRun Deployment

Set these environment variables in your CloudRun service:

```bash
AZURE_CLIENT_ID=your-client-id-here
AZURE_CLIENT_SECRET=your-client-secret-here
AZURE_TENANT_ID=your-tenant-id-here
AZURE_ADMIN_GROUP_ID=your-admin-group-object-id
AZURE_USER_GROUP_ID=your-user-group-object-id
SPRING_PROFILES_ACTIVE=prod-gcp
```

### For Local Testing with Azure AD

Create a `.env` file or set environment variables:

```bash
export AZURE_CLIENT_ID="your-client-id-here"
export AZURE_CLIENT_SECRET="your-client-secret-here"
export AZURE_TENANT_ID="your-tenant-id-here"
export AZURE_ADMIN_GROUP_ID="your-admin-group-object-id"
export AZURE_USER_GROUP_ID="your-user-group-object-id"
export SPRING_PROFILES_ACTIVE="int-gcp"
```

## Step 6: Verify Setup

### Test Authentication Flow

1. Start the application
2. Navigate to the application URL
3. You should be redirected to Azure AD login
4. Sign in with a user account that belongs to one of the security groups
5. After successful authentication, check the logs:
   ```
   Granted USER role to user: user@example.com
   ```
   or
   ```
   Granted ADMIN role to user: admin@example.com
   ```

### Troubleshooting

#### Users can't log in
- Verify the user is a member of at least one security group (Admin or User)
- Check that group claims are configured in Token configuration
- Ensure admin consent was granted for GroupMember.Read.All permission

#### No roles assigned after login
- Check application logs for warnings about missing 'groups' claim
- Verify group Object IDs match environment variables exactly
- Confirm Token configuration includes "Groups assigned to the application"

#### Error: "AADSTS50011: The reply URL specified in the request does not match"
- Verify redirect URI in Azure AD matches exactly: `https://your-domain/login/oauth2/code/azure`
- Check for trailing slashes, protocol (https vs http), etc.

#### Groups claim missing from token
- Ensure you selected "Security groups" in Token configuration
- Verify GroupMember.Read.All permission has admin consent
- Check that the user is actually a member of a group

## Security Best Practices

1. **Client Secret Rotation**: Rotate client secrets regularly (every 6-12 months)
2. **Least Privilege**: Only grant necessary API permissions
3. **Group Management**: Regularly review group memberships
4. **Audit Logs**: Monitor Azure AD sign-in logs for suspicious activity
5. **Conditional Access**: Consider implementing Azure AD Conditional Access policies

## Role Mapping Logic

The application maps Azure AD groups to roles as follows:

- If user is in `AZURE_ADMIN_GROUP_ID` → Granted `ROLE_ADMIN` + `ROLE_USER`
- If user is in `AZURE_USER_GROUP_ID` → Granted `ROLE_USER`
- If user is in neither group → No roles assigned (access denied)

Admins automatically receive USER role in addition to ADMIN role.

## Testing Locally with Dev Profile

For local development without Azure AD, use the `dev-local` profile:

```bash
SPRING_PROFILES_ACTIVE=dev-local mvn spring-boot:run
```

Test credentials:
- **Regular user**: username=`user`, password=`userpass`
- **Admin**: username=`admin`, password=`adminpass`

## References

- [Microsoft identity platform documentation](https://docs.microsoft.com/en-us/azure/active-directory/develop/)
- [Spring Security OAuth2 documentation](https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html)
- [Azure AD app registration guide](https://docs.microsoft.com/en-us/azure/active-directory/develop/quickstart-register-app)
