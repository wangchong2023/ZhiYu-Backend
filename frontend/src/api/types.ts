export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  requestId: string;
  timestamp: number;
}

export interface PaginatedData<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
  pages: number;
}

export interface LoginRequest {
  username: string;
  password: string;
  captchaToken?: string;
  captchaCode?: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
  totpRequired: boolean;
  isNewUser?: boolean;
}

export interface UserDto {
  userId: number;
  username: string;
  email: string;
  mobile: string;
  createdAt: string;
  status: string;
  scope: string;
}

export interface IdentityDto {
  identityId: number;
  provider: string;
  openid: string;
  nickname?: string;
  avatarUrl?: string;
  createdAt: string;
  enabled: number;
}

export interface WebAuthnCredentialDto {
  credentialId: string;
  deviceName?: string;
  createdAt: string;
  lastUsedTime?: string;
}

export interface WebAuthnStartResult {
  challengeId: string;
  optionsJson: string;
}

export interface LoginLogDto {
  id: number;
  username: string;
  action: string;
  type: string;
  result: string;
  ip: string;
  device: string;
  location: string;
  time: string;
}

export interface IdentityChangeDto {
  id: number;
  userId: number;
  action: string;
  identityType: string;
  sourceIp: string;
  createdAt: string;
}

export interface StatsOverview {
  newUsers: number;
  activeSubs: number;
  revenue: number;
  onlineUsers: number;
  todayRegistrations: number;
  todayLogins: number;
  dau: number;
  loginSuccessRate: number;
  registrationChange: number;
  loginChange: number;
}

export interface TrendPoint {
  date: string;
  value: number;
}

export interface DistributionItem {
  name: string;
  value: number;
}

export interface HealthDto {
  component: string;
  status: string;
  instanceCount: number;
  responseTimeMs?: number;
  detail?: string;
}

export interface MetricPoint {
  timestamp: number;
  value: number;
}

export interface MetricsDto {
  qps: MetricPoint[];
  latencyP50: MetricPoint[];
  latencyP95: MetricPoint[];
  latencyP99: MetricPoint[];
  errorRate: MetricPoint[];
}

export interface AlertDto {
  alertName: string;
  severity: string;
  condition: string;
  currentValue: string;
  status: string;
  firedAt: string;
}

export interface LoggerDto {
  name: string;
  configuredLevel: string;
  effectiveLevel: string;
}

export interface LogLevelHistoryDto {
  id: number;
  loggerName: string;
  oldLevel: string;
  newLevel: string;
  changedBy: string;
  expireAt: string | null;
  rolledBackAt: string | null;
  createdAt: string;
}

export interface AccessLogDto {
  id: number;
  time: string;
  ip: string;
  method: string;
  path: string;
  statusCode: number;
  responseTimeMs: number;
  userAgent: string;
}

export interface SlowQueryDto {
  id: number;
  time: string;
  sqlSummary: string;
  durationMs: number;
  source: string;
}

export interface AdminOperationDto {
  id: number;
  username: string;
  action: string;
  target: string;
  targetId: number;
  ip: string;
  time: string;
}

export interface TrendItem {
  date: string;
  newUsers: number;
  activeUsers: number;
}

// ── Subscription ──

export interface SubscriptionDto {
  id: number;
  userId: number;
  username: string;
  planKey: string;
  planName: string;
  status: string;
  startDate: string;
  endDate: string;
  autoRenew: number;
  createdAt: string;
}

export interface PaymentDto {
  id: number;
  orderId: number;
  userId: number;
  username: string;
  channel: string;
  transactionId: string;
  amount: number;
  currency: string;
  status: string;
  paidAt: string;
  createdAt: string;
}

export interface RefundDto {
  id: number;
  refundNo: string;
  userId: number;
  username: string;
  orderId: number;
  orderNo: string;
  amount: number;
  reason: string;
  status: string;
  reviewerId: number;
  reviewNote: string;
  appliedAt: string;
  reviewedAt: string;
}

export interface RefundReviewRequest {
  decision: string;
  note?: string;
}

// ── Admin RBAC ──

export interface RoleDto {
  roleId: number;
  roleName: string;
  roleCode: string;
  enabled: number;
  description: string;
}

export interface AssignRoleRequest {
  roleId: number;
}

export interface CreateAdminRequest {
  username: string;
  password: string;
  email: string;
}

export interface ResetPasswordRequest {
  newPassword: string;
}

// ── Notification ──

export interface NotificationTemplateDto {
  id: number;
  templateKey: string;
  type: string;
  subject: string;
  body: string;
  variablesJson: string;
  description: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateTemplateRequest {
  subject: string;
  body: string;
  variablesJson?: string;
  description?: string;
  isActive?: boolean;
}

// ── Config ──

export interface VersionDto {
  version: string;
  buildTime: string;
  commitId: string;
  branch: string;
}

export interface PodStatusDto {
  name: string;
  namespace: string;
  ready: string;
  status: string;
  restarts: number;
  startTime: string;
  lastRestartTime: string;
  node: string;
}

export interface ConfigHistoryDto {
  id: number;
  groupId: string;
  dataId: string;
  format: string;
  version: number;
  operatorId: number;
  operatorType: string;
  changeSummary: string;
  createdAt: string;
}
