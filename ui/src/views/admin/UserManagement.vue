<script setup>
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { showAppMessage } from '../../utils/ui-feedback'

const API_BASE = '/api'

const loading = ref(false)
const saving = ref(false)
const users = ref([])
const roleOptions = ref([])
const keyword = ref('')
const page = ref(1)
const pageSize = ref(10)
const totalElements = ref(0)
const totalPages = ref(0)

const editDialog = ref(false)
const editForm = ref({
  id: null,
  username: '',
  email: '',
  isActive: true,
  roleCodeList: []
})

const roleCodeToName = computed(() => {
  const map = {}
  for (const role of roleOptions.value) {
    map[role.roleCode] = role.roleName || role.roleCode
  }
  return map
})

const roleItems = computed(() => roleOptions.value.map(role => ({
  title: role.roleName || role.roleCode,
  value: role.roleCode,
  subtitle: role.description || ''
})))

const fetchRoleOptions = async () => {
  try {
    const res = await axios.get(`${API_BASE}/users/roles`)
    if (res.data?.code === 200) {
      roleOptions.value = res.data.data || []
    }
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '获取角色列表失败', 'error')
  }
}

const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await axios.get(`${API_BASE}/users`, {
      params: {
        page: page.value - 1,
        pageSize: pageSize.value,
        keyword: keyword.value?.trim() || undefined
      }
    })

    if (res.data?.code === 200 && res.data?.data) {
      const data = res.data.data
      users.value = data.content || []
      totalElements.value = Number(data.totalElements || 0)
      totalPages.value = Number(data.totalPages || 0)

      const current = Number(data.currentPage || 0) + 1
      if (current !== page.value) {
        page.value = current
      }
    }
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '获取用户列表失败', 'error')
  } finally {
    loading.value = false
  }
}

const searchUsers = async () => {
  page.value = 1
  await fetchUsers()
}

const resetSearch = async () => {
  keyword.value = ''
  page.value = 1
  await fetchUsers()
}

const onPageChange = async (newPage) => {
  page.value = newPage
  await fetchUsers()
}

const openEditDialog = (user) => {
  editForm.value = {
    id: user.id,
    username: user.username || '',
    email: user.email || '',
    isActive: user.isActive !== false,
    roleCodeList: [...(user.roleCodeList || [])]
  }
  editDialog.value = true
}

const submitEdit = async () => {
  if (!editForm.value.username?.trim()) {
    showAppMessage('用户名不能为空', 'warning')
    return
  }

  if (!editForm.value.roleCodeList || editForm.value.roleCodeList.length === 0) {
    showAppMessage('请至少选择一个角色', 'warning')
    return
  }

  saving.value = true
  try {
    const payload = {
      username: editForm.value.username.trim(),
      email: editForm.value.email?.trim() || null,
      isActive: !!editForm.value.isActive,
      roleCodeList: editForm.value.roleCodeList
    }

    const res = await axios.put(`${API_BASE}/users/${editForm.value.id}`, payload)
    if (res.data?.code === 200) {
      showAppMessage('用户更新成功', 'success')
      editDialog.value = false
      await fetchUsers()
    } else {
      showAppMessage(res.data?.msg || '用户更新失败', 'error')
    }
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '用户更新失败', 'error')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await fetchRoleOptions()
  await fetchUsers()
})
</script>

<template>
  <div>
    <v-card class="mb-4">
      <v-card-text class="d-flex flex-wrap gap-3 align-center">
        <v-text-field
          v-model="keyword"
          label="搜索用户名或邮箱"
          variant="outlined"
          density="comfortable"
          prepend-inner-icon="mdi-magnify"
          hide-details
          class="flex-grow-1"
          style="min-width: 260px"
          @keyup.enter="searchUsers"
        />
        <v-btn color="primary" @click="searchUsers">
          <v-icon start>mdi-magnify</v-icon>
          搜索
        </v-btn>
        <v-btn variant="outlined" @click="resetSearch">
          <v-icon start>mdi-refresh</v-icon>
          重置
        </v-btn>
      </v-card-text>
    </v-card>

    <v-card>
      <v-card-title class="d-flex align-center justify-space-between">
        <span>
          <v-icon start>mdi-account-cog</v-icon>
          用户管理
        </span>
        <v-chip color="primary" variant="tonal">共 {{ totalElements }} 个用户</v-chip>
      </v-card-title>

      <v-card-text>
        <v-data-table
          :headers="[
            { title: 'ID', key: 'id', width: '80px' },
            { title: '用户名', key: 'username' },
            { title: '邮箱', key: 'email' },
            { title: '角色', key: 'roles', sortable: false },
            { title: '状态', key: 'isActive', width: '100px' },
            { title: '操作', key: 'actions', sortable: false, width: '120px' }
          ]"
          :items="users"
          :loading="loading"
          :items-per-page="pageSize"
          item-value="id"
          hide-default-footer
        >
          <template #item.email="{ item }">
            {{ item.email || '-' }}
          </template>

          <template #item.roles="{ item }">
            <div class="d-flex flex-wrap ga-1">
              <v-chip
                v-for="roleCode in item.roleCodeList || []"
                :key="`${item.id}-${roleCode}`"
                size="small"
                color="primary"
                variant="outlined"
              >
                {{ roleCodeToName[roleCode] || roleCode }}
              </v-chip>
              <span v-if="!item.roleCodeList || item.roleCodeList.length === 0">-</span>
            </div>
          </template>

          <template #item.isActive="{ item }">
            <v-chip :color="item.isActive ? 'success' : 'error'" size="small" variant="tonal">
              {{ item.isActive ? '启用' : '禁用' }}
            </v-chip>
          </template>

          <template #item.actions="{ item }">
            <v-btn size="small" color="primary" variant="tonal" @click="openEditDialog(item)">
              编辑
            </v-btn>
          </template>
        </v-data-table>

        <div class="d-flex justify-end mt-4">
          <v-pagination
            v-model="page"
            :length="Math.max(totalPages, 1)"
            :total-visible="7"
            @update:model-value="onPageChange"
          />
        </div>
      </v-card-text>
    </v-card>

    <v-dialog v-model="editDialog" max-width="680">
      <v-card>
        <v-card-title>
          <v-icon start>mdi-account-edit</v-icon>
          编辑用户
        </v-card-title>

        <v-card-text class="pt-4">
          <v-row>
            <v-col cols="12" md="6">
              <v-text-field
                v-model="editForm.username"
                label="用户名"
                variant="outlined"
                required
              />
            </v-col>

            <v-col cols="12" md="6">
              <v-text-field
                v-model="editForm.email"
                label="邮箱"
                variant="outlined"
                type="email"
              />
            </v-col>

            <v-col cols="12">
              <v-select
                v-model="editForm.roleCodeList"
                :items="roleItems"
                item-title="title"
                item-value="value"
                label="用户角色"
                variant="outlined"
                multiple
                chips
                closable-chips
                persistent-hint
                hint="可多选，至少保留一个角色"
              >
                <template #item="{ props, item }">
                  <v-list-item v-bind="props" :subtitle="item.raw.subtitle" />
                </template>
              </v-select>
            </v-col>

            <v-col cols="12">
              <v-switch
                v-model="editForm.isActive"
                label="账号启用"
                color="primary"
                inset
              />
            </v-col>
          </v-row>
        </v-card-text>

        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="editDialog = false">取消</v-btn>
          <v-btn color="primary" :loading="saving" @click="submitEdit">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
