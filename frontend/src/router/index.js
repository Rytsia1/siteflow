import { createRouter, createWebHistory } from 'vue-router'
import { isAuthenticated } from '../auth'
import LoginView from '../views/LoginView.vue'
import InventoryList from '../views/InventoryList.vue'
import BorrowForm from '../views/BorrowForm.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView },
    { path: '/', redirect: '/inventory' },
    { path: '/inventory', name: 'inventory', component: InventoryList },
    { path: '/borrow', name: 'borrow', component: BorrowForm },
  ],
})

router.beforeEach((to) => {
  if (to.name !== 'login' && !isAuthenticated()) {
    return { name: 'login' }
  }
  if (to.name === 'login' && isAuthenticated()) {
    return { name: 'inventory' }
  }
  return true
})

export default router
