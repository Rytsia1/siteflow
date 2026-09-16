import { createRouter, createWebHistory } from 'vue-router'
import { auth, isAuthenticated } from '../auth'
import LoginView from '../views/LoginView.vue'
import InventoryList from '../views/InventoryList.vue'
import BorrowForm from '../views/BorrowForm.vue'
import ApprovalDashboard from '../views/ApprovalDashboard.vue'
import AssetScanner from '../views/AssetScanner.vue'
import ProcurementView from '../views/ProcurementView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView },
    { path: '/', redirect: '/inventory' },
    { path: '/inventory', name: 'inventory', component: InventoryList },
    { path: '/borrow', name: 'borrow', component: BorrowForm },
    {
      path: '/approvals',
      name: 'approvals',
      component: ApprovalDashboard,
      meta: { roles: ['ADMIN'] },
    },
    {
      path: '/assets',
      name: 'assets',
      component: AssetScanner,
      meta: { roles: ['ADMIN', 'WAREHOUSE_STAFF'] },
    },
    { path: '/procurement', name: 'procurement', component: ProcurementView },
  ],
})

router.beforeEach((to) => {
  if (to.name !== 'login' && !isAuthenticated()) {
    return { name: 'login' }
  }
  if (to.name === 'login' && isAuthenticated()) {
    return { name: 'inventory' }
  }
  if (to.meta.roles && !to.meta.roles.includes(auth.role)) {
    return { name: 'inventory' }
  }
  return true
})

export default router
