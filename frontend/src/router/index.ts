import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { trySilentRefresh } from '@/api/http'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/public'
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/change-password',
    name: 'change-password',
    component: () => import('@/views/auth/ChangePasswordView.vue'),
    meta: { requiresAuth: true, allowPasswordChange: true }
  },
  {
    path: '/public',
    component: () => import('@/layouts/PublicLayout.vue'),
    meta: { public: true },
    children: [
      {
        path: '',
        name: 'public-home',
        component: () => import('@/views/public/PublicHomeView.vue'),
        meta: { public: true }
      }
    ]
  },
  {
    path: '/portal',
    component: () => import('@/layouts/PortalLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'portal-home',
        component: () => import('@/views/portal/PortalHomeView.vue'),
        meta: { requiresAuth: true }
      }
    ]
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: '',
        redirect: '/admin/iam/departments'
      },
      {
        path: 'iam/departments',
        name: 'admin-iam-departments',
        component: () => import('@/views/admin/iam/DepartmentManageView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true, permission: 'iam:dept:read' }
      },
      {
        path: 'iam/users',
        name: 'admin-iam-users',
        component: () => import('@/views/admin/iam/UserManageView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true, permission: 'iam:user:read' }
      },
      {
        path: 'iam/roles',
        name: 'admin-iam-roles',
        component: () => import('@/views/admin/iam/RoleManageView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true, permission: 'iam:role:read' }
      },
      {
        path: 'iam/audit-logs',
        name: 'admin-iam-audit-logs',
        component: () => import('@/views/admin/iam/AuditLogView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true, permission: 'iam:audit:read' }
      },
      {
        path: 'attendance/calendar',
        name: 'admin-attendance-calendar',
        component: () => import('@/views/admin/attendance/CalendarManageView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true, permission: 'att:calendar:read' }
      },
      {
        path: 'attendance/monthly-close',
        name: 'admin-attendance-monthly-close',
        component: () => import('@/views/admin/attendance/MonthlyCloseView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true, permission: 'att:monthly-close:manage' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/public'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  // Attempt silent session restore once on initial page load
  if (!authStore.initialized) {
    await trySilentRefresh()
  }

  // Enforce Mandatory Password Change Gate (ADR-0004)
  if (authStore.isAuthenticated && authStore.mustChangePassword && !to.meta.allowPasswordChange) {
    return { name: 'change-password' }
  }

  // Redirect already authenticated users away from /login
  if (to.name === 'login' && authStore.isAuthenticated) {
    if (authStore.mustChangePassword) {
      return { name: 'change-password' }
    }
    return authStore.canAccessAdmin ? { path: '/admin/iam/departments' } : { name: 'portal-home' }
  }

  // Protect authenticated routes
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // Protect admin console routes
  if (to.meta.requiresAdmin && !authStore.canAccessAdmin) {
    return { name: 'portal-home' }
  }

  return true
})

export default router
