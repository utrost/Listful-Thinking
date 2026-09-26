<template>
  <main class="app-shell" :class="{ 'signed-in': currentUser && !publicToken }" :aria-busy="actionPending">
    <section class="hero">
      <p v-if="!currentUser && !publicToken" class="eyebrow">{{ t('app.tagline') }}</p>
      <div class="header-row">
        <div>
          <h1>{{ t('app.title') }}</h1>
          <p v-if="!currentUser && !publicToken" class="intro">{{ t('app.intro') }}</p>
        </div>
        <div class="account-actions">
          <label class="language-select"><span class="sr-only">{{ t('ux.language') }}</span><select v-model="locale" :aria-label="t('ux.language')"><option value="en">English</option><option value="de">Deutsch</option></select></label>
          <span v-if="currentUser" class="account-name">{{ currentUser.username }}</span>
          <button v-if="currentUser?.role === 'ADMIN'" type="button" class="secondary subtle" :aria-pressed="adminView" @click="adminView = !adminView">{{ adminView ? t('ux.backToLists') : t('ux.administration') }}</button>
          <button v-if="currentUser" type="button" class="secondary subtle" :disabled="actionPending" @click="run(handleLogout)">{{ t('auth.logout') }}</button>
        </div>
      </div>

      <p v-if="message" ref="statusElement" tabindex="-1" class="status" :role="messageKind === 'error' ? 'alert' : 'status'" :aria-live="messageKind === 'error' ? 'assertive' : 'polite'">{{ message }}</p>

      <p v-if="actionPending" class="pending" role="status">{{ t('app.working') }}</p>
      <fieldset class="app-content" :disabled="actionPending">
      <section v-if="publicToken" class="panel public-panel">
        <p class="eyebrow">{{ t('ux.sharedWithYou') }}</p>
        <h2>{{ publicList?.title ?? t('sharing.publicList') }}</h2>
        <p v-if="publicList?.description">{{ publicList.description }}</p>
        <p v-if="publicList" class="muted">{{ t(`sharing.modes.${publicList.mode}`) }}</p>
        <p class="muted">{{ t(publicList?.mode === 'VIEW' ? 'ux.publicViewHelp' : 'ux.publicClaimHelp') }}</p>
        <ul class="item-list">
          <li v-for="item in publicList?.items ?? []" :key="item.id">
            <PrivateImage v-if="item.imageUrl" :src="item.imageUrl" :alt="item.name" />
            <span>
              <strong>{{ item.name }}</strong>
              <small v-if="item.description">{{ item.description }}</small>
              <a v-if="safeProductUrl(item.url)" :href="safeProductUrl(item.url)!" target="_blank" rel="noopener noreferrer">{{ t('items.viewProduct') }}</a>
              <small v-if="item.dueDate">{{ formatDate(item.dueDate) }}</small>
                  <small>{{ t(`items.statuses.${item.status}`) }}<template v-if="item.price != null"> · {{ formatPrice(item.price, item.priceCurrency) }}</template></small>
            </span>
            <form v-if="publicList?.mode !== 'VIEW' && item.status === 'OPEN'" class="claim-form" @submit.prevent="handleClaimPublicItem(item.id)">
              <input v-model="guestName" :aria-label="t('sharing.guestName')" :placeholder="t('sharing.guestName')" required />
              <button type="submit">{{ publicList?.mode === 'SIGNUP' ? t('sharing.signup') : t('sharing.claim') }}</button>
            </form>
          </li>
        </ul>
      </section>

      <section v-else-if="!currentUser" class="panel-grid">
        <form v-if="!resetToken" class="panel" @submit.prevent="handleLogin">
          <h2>{{ t('auth.login') }}</h2>
          <label>{{ t('auth.username') }}<input v-model="loginForm.username" :aria-label="t('auth.username')" required /></label>
          <label>{{ t('auth.password') }}<input v-model="loginForm.password" :aria-label="t('auth.password')" type="password" required /></label>
          <button type="submit">{{ t('auth.login') }}</button>
        </form>
        <details v-if="!resetToken" class="panel recovery-panel">
          <summary>{{ t('auth.recovery') }}</summary>
          <h2>{{ t('auth.recovery') }}</h2>
          <p v-if="authSettings?.emailRecoveryAvailable === false" role="status">{{ t('errors.email_unavailable') }}</p>
          <p>{{ t('auth.recoveryHelp') }}</p>
          <label>{{ t('auth.email') }}<input ref="recoveryEmailInput" v-model="emailAuthForm.email" :aria-label="t('auth.email')" type="email" required :aria-invalid="recoveryEmailInvalid" aria-describedby="recovery-email-error" @input="recoveryEmailInvalid = false" /></label>
          <p v-if="recoveryEmailInvalid" id="recovery-email-error" role="alert">{{ t('auth.validEmailRequired') }}</p>
          <label>{{ t('auth.optionalUsername') }}<input v-model="emailAuthForm.username" :aria-label="t('auth.optionalUsername')" autocomplete="username" /></label>
          <div class="button-row">
            <button type="button" class="secondary" :disabled="authSettings?.emailRecoveryAvailable === false" @click="handleRequestMagicLink">{{ t('auth.magicLink') }}</button>
            <button type="button" class="secondary" :disabled="authSettings?.emailRecoveryAvailable === false" @click="handleRequestPasswordReset">{{ t('auth.passwordReset') }}</button>
          </div>
        </details>
        <form v-if="!resetToken && (authSettings?.registrationAvailable ?? false)" class="panel" @submit.prevent="handleRegister">
          <h2>{{ t('auth.register') }}</h2>
          <label>{{ t('auth.username') }}<input v-model="registerForm.username" :aria-label="t('auth.username')" required minlength="3" /></label>
          <label>{{ t('auth.email') }}<input v-model="registerForm.email" :aria-label="t('auth.email')" type="email" /></label>
          <label>{{ t('auth.password') }}<input v-model="registerForm.password" :aria-label="t('auth.password')" type="password" required minlength="8" /></label>
          <button type="submit">{{ t('auth.register') }}</button>
        </form>
        <section v-else-if="!resetToken && authSettings && !authSettings.registrationAvailable" class="panel">
          <h2>{{ t('auth.register') }}</h2>
          <p class="muted">{{ t('auth.registrationDisabled') }}</p>
        </section>

        <form v-if="resetToken" class="panel" @submit.prevent="handleConsumePasswordReset">
          <h2>{{ t('auth.setNewPassword') }}</h2>
          <p class="muted">{{ t('ux.resetHelp') }}</p>
            <label>{{ t('auth.newPassword') }}<input v-model="resetPasswordForm.password" :aria-label="t('auth.newPassword')" type="password" required minlength="8" autocomplete="new-password" /></label>
            <button type="submit" class="secondary">{{ t('auth.setNewPassword') }}</button>
        </form>
      </section>

      <section v-else class="workspace">
        <section v-show="adminView" class="admin-destination">
          <h2>{{ t('ux.administration') }}</h2>
          <p class="muted">{{ t('ux.adminHelp') }}</p>
        <AdminPanel v-if="currentUser.role === 'ADMIN'"
          :admin-settings="adminSettings" :admin-users="adminUsers" :admin-lists="adminLists" :admin-user-form="adminUserForm"
          @refresh="run(handleLoadAdminPanel)" @registration="handleToggleRegistration" @create-user="handleAdminCreateUser" @active="handleToggleUserActive" />
        </section>
        <div v-show="!adminView">
        <nav class="workspace-navigation" :aria-label="t('planning.navigation')">
          <button v-for="view in (['lists', 'today', 'archive', 'templates', 'trash'] as const)" :key="view" type="button" :aria-current="hubView === view ? 'page' : undefined" @click="showHub(view)">{{ t(`planning.views.${view}`) }}</button>
        </nav>
        <div v-if="undoAction" class="undo-notice" role="status"><span>{{ undoAction.label }}</span><button type="button" class="secondary" @click="handleUndo">{{ t('planning.undo') }}</button><button type="button" class="secondary subtle" @click="undoAction = null">{{ t('planning.dismiss') }}</button></div>
        <PlanningHub v-if="hubView !== 'lists'" :key="hubView" :view="hubView" :overview="overview" :library="libraryLists" :trash-items="trashItems" :pending="actionPending" :ready="hubReady"
          @refresh="showHub(hubView)" @open="openHubList" @complete="completeAgenda" @restore="handleRestoreList" @restore-item="handleRestoreItem" @instantiate="handleInstantiateTemplate" @delete="handleDeleteTemplate" />
        <div v-show="hubView === 'lists'">
        <div class="section-header">
          <h2>{{ t('lists.title') }}</h2>
          <p class="muted workspace-intro">{{ t('ux.listsHelp') }}</p>
        </div>

        <section v-if="notifications.length" class="panel notifications-panel">
          <h3>{{ t('notifications.title') }}</h3>
          <ul class="notification-list">
            <li v-for="notification in notifications" :key="notification.id">
              <span>{{ notification.message }}</span>
              <button type="button" class="secondary subtle" @click="handleMarkNotificationRead(notification.id)">{{ t('notifications.markRead') }}</button>
            </li>
          </ul>
        </section>





        <div class="content-grid">
          <aside class="list-navigation" :aria-label="t('lists.title')">
            <details ref="newListDisclosure" class="new-list-disclosure">
              <summary>{{ t('ux.newList') }}</summary>
                      <form class="inline-form new-list-form" @submit.prevent="handleCreateList">
          <label>{{ t('lists.newTitle') }}<input v-model="listForm.title" :aria-label="t('lists.newTitle')" :placeholder="t('lists.newTitle')" required /></label>
          <label>{{ t('lists.description') }}<input v-model="listForm.description" :aria-label="t('lists.description')" :placeholder="t('lists.description')" /></label>
          <label>{{ t('lists.type') }}<select v-model="listForm.type" :aria-label="t('lists.type')">
            <option value="WISH">{{ t('lists.types.WISH') }}</option>
            <option value="TODO">{{ t('lists.types.TODO') }}</option>
            <option value="GROCERY">{{ t('lists.types.GROCERY') }}</option>
            <option value="CHORE">{{ t('lists.types.CHORE') }}</option>
            <option value="EVENT">{{ t('lists.types.EVENT') }}</option>
          </select></label>
          <label v-if="newListRules.showTargetDate">{{ t('lists.targetDate') }}<input
            v-model="listForm.targetDate"
            type="datetime-local"
            :required="newListRules.requireTargetDate"
            :aria-label="t('lists.targetDate')"
            :placeholder="t('lists.targetDate')"
          /></label>
          <p class="form-help">{{ t(`ux.purpose.${listForm.type}`) }}</p>
          <button type="submit">{{ t('lists.create') }}</button>
        </form>
            </details>
            <label v-if="lists.length > 1" class="list-search">{{ t('ux.findList') }}<input v-model="listQuery" type="search" /></label>
            <label v-if="lists.length" class="mobile-list-select">{{ t('ux.chooseList') }}<select :aria-label="t('ux.chooseList')" :value="selectedList?.id" @change="handleMobileListSelection"><option v-for="list in lists" :key="list.id" :value="list.id">{{ list.title }} · {{ t(`lists.types.${list.type}`) }}</option></select></label>
            <ul class="list-cards">
            <li v-for="list in navigationLists" :key="list.id" :class="{ selected: selectedList?.id === list.id }">
              <button type="button" class="text-button" :aria-current="selectedList?.id === list.id ? 'true' : undefined" @click="run(() => selectList(list), false)">
                <strong>{{ list.title }}</strong>
                <small>{{ t(`lists.types.${list.type}`) }}</small>
              </button>
            </li>
            </ul>
            <p v-if="listQuery && !navigationLists.length" class="muted">{{ t('ux.noListsFound') }}</p>
          </aside>

          <section v-if="selectedList" class="panel detail-panel">
            <div class="section-header">
              <div>
                <h3>{{ selectedList.title }}</h3>
                <small>{{ t(`lists.types.${selectedList.type}`) }}<template v-if="selectedList.targetDate"> · {{ formatDate(selectedList.targetDate) }}</template></small>
              </div>
              <span class="list-progress">{{ t('ux.itemCount', { open: items.filter(item => item.status === 'OPEN').length, total: items.length }) }}</span>
            </div>
            <p v-if="selectedList.description" class="list-description">{{ selectedList.description }}</p>
            <p v-if="selectedList.access !== 'OWNER'" class="access-note">{{ t(selectedList.access === 'READ' ? 'ux.readOnlyHelp' : 'ux.contributorHelp') }}</p>
            <p v-if="selectedList.archived" class="access-note">{{ t('planning.archivedPreview') }}</p>
            <p v-if="selectedList.template" class="access-note">{{ t('planning.templatePreview') }}</p>
            <nav v-if="selectedList.access === 'OWNER'" class="list-tabs" :aria-label="t('ux.listSections')">
              <button v-for="view in (selectedList.archived || selectedList.template ? ['items', 'settings'] as const : ['items', 'sharing', 'settings'] as const)" :key="view" type="button" :aria-current="listView === view ? 'page' : undefined" @click="listView = view">{{ t(`ux.views.${view}`) }}</button>
            </nav>
            <section v-show="listView === 'settings'" class="list-settings">
              <h4>{{ t('ux.views.settings') }}</h4>
              <p class="muted">{{ t('ux.settingsHelp') }}</p>
              <div class="button-row">
                <button v-if="selectedList.access === 'OWNER' && !selectedList.archived" type="button" class="secondary subtle" @click="handleStartEditList">{{ t('lists.edit') }}</button>
                <button v-if="selectedList.access === 'OWNER'" type="button" class="secondary subtle" @click="handleCloneList">{{ t('lists.duplicate') }}</button>
                <button v-if="selectedList.access === 'OWNER' && pendingDeleteListId !== selectedList.id" type="button" class="danger" @click="handleRequestDeleteList(selectedList.id)">{{ t('lists.delete') }}</button>
                <template v-else-if="selectedList.access === 'OWNER'">
                  <button type="button" class="danger" @click="handleConfirmDeleteList(selectedList.id)">{{ t('lists.deleteConfirm') }}</button>
                  <button type="button" class="secondary subtle" @click="pendingDeleteListId = null">{{ t('lists.deleteCancel') }}</button>
                </template>
              </div>
              <div v-if="selectedList.access === 'OWNER'" class="button-row lifecycle-actions">
                <button v-if="!selectedList.template && !selectedList.archived" type="button" class="secondary" @click="handleArchiveSelected">{{ t('planning.archiveList') }}</button>
                <button v-if="selectedList.archived" type="button" @click="handleRestoreList(selectedList)">{{ t('planning.restore') }}</button>
                <button v-if="!selectedList.template" type="button" class="secondary" @click="templateTitle = selectedList.title; savingTemplate = true">{{ t('planning.saveTemplate') }}</button>
              </div>
              <form v-if="savingTemplate" class="inline-form save-template-form" @submit.prevent="handleSaveTemplate">
                <label>{{ t('planning.templateTitle') }}<input v-model="templateTitle" required maxlength="255" /></label><button type="submit">{{ t('planning.saveTemplate') }}</button><button type="button" class="secondary" @click="savingTemplate = false">{{ t('lists.cancel') }}</button>
              </form>
            <form v-if="editingList" class="inline-form edit-list-form" @submit.prevent="handleSaveEditedList">
              <label>{{ t('lists.newTitle') }}<input v-model="editListForm.title" :aria-label="t('lists.newTitle')" :placeholder="t('lists.newTitle')" required /></label>
              <label>{{ t('lists.description') }}<input v-model="editListForm.description" :aria-label="t('lists.description')" :placeholder="t('lists.description')" /></label>
              <label>{{ t('lists.type') }}<select v-model="editListForm.type" :disabled="items.length > 0" :aria-label="t('lists.type')">
                <option value="WISH">{{ t('lists.types.WISH') }}</option>
                <option value="TODO">{{ t('lists.types.TODO') }}</option>
                <option value="GROCERY">{{ t('lists.types.GROCERY') }}</option>
                <option value="CHORE">{{ t('lists.types.CHORE') }}</option>
                <option value="EVENT">{{ t('lists.types.EVENT') }}</option>
              </select></label>
              <label v-if="editListRules.showTargetDate && !selectedList.template">{{ t('lists.targetDate') }}<input
                v-model="editListForm.targetDate"
                type="datetime-local"
                :required="editListRules.requireTargetDate && !selectedList.template"
                :aria-label="t('lists.targetDate')"
                :placeholder="t('lists.targetDate')"
              /></label>
              <button type="submit">{{ t('lists.save') }}</button>
              <button type="button" class="secondary subtle" @click="handleCancelEditList">{{ t('lists.cancel') }}</button>
            </form>
            <p v-if="items.length" class="muted">{{ t('ux.typeLocked') }}</p>
            </section>

            <section v-if="selectedList.access === 'OWNER' && !selectedList.archived && !selectedList.template" v-show="listView === 'sharing'" class="share-panel">
              <h4>{{ t('sharing.title') }}</h4>
              <p class="muted">{{ t('ux.sharingHelp') }}</p>
              <h5>{{ t('ux.publicLink') }}</h5>
              <div class="button-row">
                <select v-model="publicShareMode" :aria-label="t('sharing.mode')">
                  <option value="VIEW">{{ t('sharing.modes.VIEW') }}</option>
                  <option v-if="selectedList.type === 'WISH'" value="WISH_CLAIM">{{ t('sharing.modes.WISH_CLAIM') }}</option>
                  <option v-if="selectedList.type !== 'WISH'" value="SIGNUP">{{ t('sharing.modes.SIGNUP') }}</option>
                </select>
                <button type="button" class="secondary" @click="handleCreatePublicShare">{{ selectedList.publicList ? t('sharing.replacePublic') : t('sharing.createPublic') }}</button>
                <button v-if="selectedList.publicList" type="button" class="danger subtle" @click="handleRevokePublicShare">{{ t('sharing.revokePublic') }}</button>
              </div>
              <p v-if="selectedList.publicList" class="muted">{{ t('sharing.activeLinkHint') }}</p>
              <p v-if="selectedList.publicList && selectedList.shareToken" class="copyable-link">{{ publicShareUrl(selectedList.shareToken) }}</p>
              <div v-if="selectedList.shareToken" class="button-row">
                <button type="button" class="secondary" @click="run(() => copyShareLink(selectedList!.shareToken!))">{{ t('sharing.copyLink') }}</button>
                <a :href="publicShareUrl(selectedList.shareToken)" target="_blank" rel="noopener noreferrer">{{ t('sharing.openLink') }}</a>
              </div>
              <h5>{{ t('ux.invitePeople') }}</h5>
              <p class="muted">{{ t('ux.inviteHelp') }}</p>
              <form class="inline-form" @submit.prevent="handleShareList">
                <input v-model="shareForm.username" :aria-label="t('sharing.username')" :placeholder="t('sharing.username')" required />
                <select v-model="shareForm.permission" :aria-label="t('sharing.permission')">
                  <option value="READ">{{ t('sharing.readOnly') }}</option>
                  <option value="CONTRIBUTE">{{ t('sharing.contribute') }}</option>
                </select>
                <button type="submit">{{ t('ux.invite') }}</button>
              </form>
              <ul class="chip-list">
                <li v-for="share in shares" :key="share.userId">
                  <span>{{ share.username }} · {{ share.permission }}</span>
                  <button type="button" class="danger subtle" @click="handleRevokeShare(share.username)">{{ t('sharing.revoke') }}</button>
                </li>
              </ul>
            </section>

            <section v-show="listView === 'items'" class="items-workspace">
              <p class="list-purpose">{{ t(`ux.purpose.${selectedList.type}`) }}</p>
            <details v-if="selectedList.access !== 'READ' && !selectedList.archived" ref="itemComposerDisclosure" class="composer-disclosure" :open="items.length === 0">
              <summary>{{ t('ux.addToList') }}</summary>
            <form class="inline-form item-composer" @paste="currentItemFields.showImageUrl && handleImagePaste($event, itemForm)" @submit.prevent="handleCreateItem">
              <div class="quick-add">
              <label>{{ t('items.newName') }}<input v-model="itemForm.name" :aria-label="t('items.newName')" :placeholder="t('items.newName')" :required="!currentItemFields.showUrl || !itemForm.url" /></label>
              <label v-if="currentItemFields.showUrl">{{ t('ux.productUrl') }}<input v-model="itemForm.url" aria-label="URL" placeholder="URL" @change="handleScrapeItemUrl" /></label>
              <label v-if="currentItemFields.showQuantity">{{ t('items.quantity') }}<input v-model="itemForm.quantity" :aria-label="t('items.quantity')" :placeholder="t('items.quantity')" /></label>
              <button type="submit">{{ t('items.create') }}</button>
              </div>
              <details ref="itemDetailsDisclosure" class="item-details">
                <summary>{{ t('ux.moreDetails') }}</summary>
                <div class="details-fields">
                <button v-if="currentItemFields.showUrl" type="button" class="secondary" @click="handleScrapeItemUrl">{{ t('items.previewUrl') }}</button>
              <label>{{ t('items.description') }}<textarea v-model="itemForm.description" :aria-label="t('items.description')" :placeholder="t('items.description')"></textarea></label>
              <label v-if="currentItemFields.showImageUrl">{{ t('items.imageUrl') }}<input v-model="itemForm.imageUrl" :aria-label="t('items.imageUrl')" :placeholder="t('items.imageUrl')" /></label>
              <label v-if="currentItemFields.showImageUrl">{{ t('items.uploadImage') }}<input type="file" :aria-label="t('items.uploadImage')" :accept="acceptedImageTypes" @change="handleImageFileInput($event, itemForm)" /></label>
              <PrivateImage class="preview" v-if="currentItemFields.showImageUrl && itemForm.imageUrl" :src="itemForm.imageUrl" :alt="itemForm.name || t('items.newName')" />
              <button v-if="currentItemFields.showImageUrl && itemForm.imageUrl" type="button" class="secondary subtle" @click="itemForm.imageUrl = ''">{{ t('items.removeImage') }}</button>
              <label v-if="currentItemFields.showPrice">{{ t('items.price') }}<input v-model.number="itemForm.price" type="number" min="0" step="0.01" :aria-label="t('items.price')" :placeholder="t('items.price')" /></label>
              <label v-if="currentItemFields.showPrice">{{ t('items.currency') }}<input v-model="itemForm.priceCurrency" :aria-label="t('items.currency')" :placeholder="t('items.currency')" maxlength="3" pattern="[A-Z]{3}" /></label>
              <label v-if="currentItemFields.showCategory">{{ t('items.category') }}<input v-model="itemForm.category" :aria-label="t('items.category')" :placeholder="t('items.category')" /></label>
              <label v-if="currentItemFields.showDueDate">{{ t('items.dueDate') }}<input v-model="itemForm.dueDate" type="datetime-local" :aria-label="t('items.dueDate')" :placeholder="t('items.dueDate')" /></label>
              <label v-if="currentItemFields.showResponsibility">{{ t('items.ownerLabel') }}<input v-model="itemForm.ownerLabel" :aria-label="t('items.ownerLabel')" :placeholder="t('items.ownerLabel')" /></label>
              <label v-if="currentItemFields.showResponsibility">{{ t('items.assistantLabels') }}<input v-model="itemForm.assistantLabels" :aria-label="t('items.assistantLabels')" :placeholder="t('items.assistantLabels')" /></label>
              <label v-if="currentItemFields.showRecurrenceRule">{{ t('ux.repeat') }}<select v-model="itemForm.recurrenceRule" :aria-label="t('ux.repeat')">
                <option v-for="option in recurrenceOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select></label>
                </div>
              </details>
            </form>
            </details>

            <div v-if="selectedList.type === 'GROCERY' && !selectedList.archived && !selectedList.template" class="button-row shop-controls">
              <label class="toggle-row">
                <input v-model="hideCompletedGroceries" type="checkbox" />
                <span>{{ t('items.hideCompleted') }}</span>
              </label>
              <button v-if="selectedList.access === 'OWNER'" type="button" class="danger subtle" @click="handleClearCompletedGroceries">{{ t('items.clearCompleted') }}</button>
            </div>

            <section class="item-review-controls" :aria-label="t('items.reviewControlsLabel')">
              <label>{{ t('items.search') }}<input v-model="itemReviewForm.query" type="search" :placeholder="t('items.searchPlaceholder')" /></label>
              <details class="filter-options"><summary>{{ t('ux.filterSort') }}</summary><div class="filter-fields">
              <label>{{ t('items.filter') }}
                <select v-model="itemReviewForm.statusFilter">
                  <option value="ALL">{{ t('items.filters.all') }}</option>
                  <option value="OPEN">{{ t('items.filters.open') }}</option>
                  <option value="COMPLETED">{{ t('items.filters.completed') }}</option>
                  <option value="CLAIMED">{{ t('items.filters.claimed') }}</option>
                  <option value="PURCHASED">{{ t('items.filters.purchased') }}</option>
                  <option value="OVERDUE">{{ t('items.filters.overdue') }}</option>
                  <option value="UPCOMING">{{ t('items.filters.upcoming') }}</option>
                </select>
              </label>
              <label>{{ t('items.sort') }}
                <select v-model="itemReviewForm.sortBy">
                  <option value="created">{{ t('items.sorts.created') }}</option>
                  <option value="dueDate">{{ t('items.sorts.dueDate') }}</option>
                  <option value="category">{{ t('items.sorts.category') }}</option>
                  <option value="status">{{ t('items.sorts.status') }}</option>
                </select>
              </label>
              </div></details>
            </section>
            <section v-if="!items.length" class="empty-state"><h4>{{ t('ux.emptyItems') }}</h4><p>{{ t(selectedList.access === 'READ' ? 'ux.emptyReadOnly' : 'ux.emptyItemsHelp') }}</p></section>
            <section v-else-if="!displayedItems.length" class="empty-state"><h4>{{ t('ux.noResults') }}</h4><p>{{ t('ux.noResultsHelp') }}</p><button type="button" class="secondary" @click="resetItemReviewForm(); hideCompletedGroceries = false">{{ t('ux.resetFilters') }}</button></section>

            <template v-if="selectedList.type === 'GROCERY'">
              <section v-for="[category, groupItems] in groceryGroups" :key="category" class="grocery-group">
                <h4>{{ category }}</h4>
                <ul class="item-list">
                  <li v-for="item in groupItems" :key="item.id" :class="{ completed: item.status === 'DONE' || item.status === 'PURCHASED' }">
                    <form v-if="editingItemId === item.id" class="inline-form edit-item-form" @submit.prevent="handleSaveEditedItem(item)">
                      <label>{{ t('items.newName') }}<input v-model="editItemForm.name" :aria-label="t('items.newName')" :placeholder="t('items.newName')" required /></label>
                      <label v-if="currentItemFields.showQuantity">{{ t('items.quantity') }}<input v-model="editItemForm.quantity" :aria-label="t('items.quantity')" :placeholder="t('items.quantity')" /></label>
                      <label v-if="currentItemFields.showCategory">{{ t('items.category') }}<input v-model="editItemForm.category" :aria-label="t('items.category')" :placeholder="t('items.category')" /></label>
                      <label v-if="currentItemFields.showResponsibility">{{ t('items.ownerLabel') }}<input v-model="editItemForm.ownerLabel" :aria-label="t('items.ownerLabel')" :placeholder="t('items.ownerLabel')" /></label>
                      <label v-if="currentItemFields.showResponsibility">{{ t('items.assistantLabels') }}<input v-model="editItemForm.assistantLabels" :aria-label="t('items.assistantLabels')" :placeholder="t('items.assistantLabels')" /></label>
                      <button type="submit">{{ t('items.save') }}</button>
                      <button type="button" class="secondary subtle" @click="handleCancelEditItem">{{ t('items.cancel') }}</button>
                    </form>
                    <span v-else class="item-content">
                      <strong>{{ item.name }}</strong>
                      <small v-if="item.quantity || item.category"><template v-if="item.quantity">{{ item.quantity }}</template><template v-if="item.quantity && item.category"> · </template><template v-if="item.category">{{ item.category }}</template></small>
                      <small v-if="item.ownerLabel || item.assistantLabels"><template v-if="item.ownerLabel">{{ t('items.ownerLabel') }}: {{ item.ownerLabel }}</template><template v-if="item.ownerLabel && item.assistantLabels"> · </template><template v-if="item.assistantLabels">{{ t('items.assistantLabels') }}: {{ item.assistantLabels }}</template></small>
                      <small v-if="item.dueDate">{{ formatDate(item.dueDate) }}</small>
                  <small>{{ t(`items.statuses.${item.status}`) }}</small>
                    </span>
                    <button v-if="!selectedList.archived && !selectedList.template && (selectedList.access === 'OWNER' || selectedList.access === 'CONTRIBUTE') && item.status === 'OPEN'" type="button" class="secondary subtle" :aria-label="`${t('items.done')}: ${item.name}`" @click="handleToggleItemDone(item)">{{ t('items.done') }}</button>
                    <button v-else-if="!selectedList.archived && !selectedList.template && (selectedList.access === 'OWNER' || selectedList.access === 'CONTRIBUTE') && item.status === 'DONE'" type="button" class="secondary subtle" :aria-label="`${t('items.reopen')}: ${item.name}`" @click="handleToggleItemDone(item)">{{ t('items.reopen') }}</button>
                <span v-if="item.importStatus === 'PENDING'" role="status">{{ t('items.importPending') }}</span>
                <details v-if="!selectedList.archived && selectedList.access !== 'READ' && editingItemId !== item.id" class="item-actions"><summary :aria-label="`${t('ux.actions')}: ${item.name}`">{{ t('ux.actions') }}</summary><div class="item-actions-menu">
                <button v-if="selectedList.access === 'OWNER' || selectedList.access === 'CONTRIBUTE'" type="button" class="secondary subtle" :aria-label="`${t('items.edit')}: ${item.name}`" @click="handleStartEditItem(item)">{{ t('items.edit') }}</button>

                <button v-if="item.importStatus === 'FAILED'" type="button" class="secondary" @click="handleRetryImport(item)">{{ t('items.retryImport') }}</button>
                <button v-if="selectedList.access === 'OWNER'" type="button" class="danger" :aria-label="`${t('items.delete')}: ${item.name}`" @click="handleDeleteItem(item.id)">{{ t('items.delete') }}</button>
                </div></details>
                  </li>
                </ul>
              </section>
            </template>

            <ul v-else class="item-list">
              <li v-for="item in displayedItems" :key="item.id" :class="{ completed: item.status === 'DONE' || item.status === 'PURCHASED' }">
                <PrivateImage v-if="item.imageUrl" :src="item.imageUrl" :alt="item.name" />
                <form v-if="editingItemId === item.id" class="inline-form edit-item-form" @paste="currentItemFields.showImageUrl && handleImagePaste($event, editItemForm)" @submit.prevent="handleSaveEditedItem(item)">
                  <label>{{ t('items.newName') }}<input v-model="editItemForm.name" :aria-label="t('items.newName')" :placeholder="t('items.newName')" required /></label>
                  <label>{{ t('items.description') }}<textarea v-model="editItemForm.description" :aria-label="t('items.description')" :placeholder="t('items.description')"></textarea></label>
                  <input v-if="currentItemFields.showUrl" v-model="editItemForm.url" aria-label="URL" placeholder="URL" />
                  <label v-if="currentItemFields.showImageUrl">{{ t('items.imageUrl') }}<input v-model="editItemForm.imageUrl" :aria-label="t('items.imageUrl')" :placeholder="t('items.imageUrl')" /></label>
                  <label v-if="currentItemFields.showImageUrl">{{ t('items.uploadImage') }}<input type="file" :aria-label="t('items.uploadImage')" :accept="acceptedImageTypes" @change="handleImageFileInput($event, editItemForm)" /></label>
                  <PrivateImage class="preview" v-if="currentItemFields.showImageUrl && editItemForm.imageUrl" :src="editItemForm.imageUrl" :alt="editItemForm.name || t('items.newName')" />
                  <button v-if="currentItemFields.showImageUrl && editItemForm.imageUrl" type="button" class="secondary subtle" @click="editItemForm.imageUrl = ''">{{ t('items.removeImage') }}</button>
                  <label v-if="currentItemFields.showPrice">{{ t('items.price') }}<input v-model.number="editItemForm.price" type="number" min="0" step="0.01" :aria-label="t('items.price')" :placeholder="t('items.price')" /></label>
              <label v-if="currentItemFields.showPrice">{{ t('items.currency') }}<input v-model="editItemForm.priceCurrency" :aria-label="t('items.currency')" :placeholder="t('items.currency')" maxlength="3" pattern="[A-Z]{3}" /></label>
                  <label v-if="currentItemFields.showQuantity">{{ t('items.quantity') }}<input v-model="editItemForm.quantity" :aria-label="t('items.quantity')" :placeholder="t('items.quantity')" /></label>
                  <label v-if="currentItemFields.showCategory">{{ t('items.category') }}<input v-model="editItemForm.category" :aria-label="t('items.category')" :placeholder="t('items.category')" /></label>
                  <label v-if="currentItemFields.showDueDate">{{ t('items.dueDate') }}<input v-model="editItemForm.dueDate" type="datetime-local" :aria-label="t('items.dueDate')" :placeholder="t('items.dueDate')" /></label>
                  <label v-if="currentItemFields.showResponsibility">{{ t('items.ownerLabel') }}<input v-model="editItemForm.ownerLabel" :aria-label="t('items.ownerLabel')" :placeholder="t('items.ownerLabel')" /></label>
                  <label v-if="currentItemFields.showResponsibility">{{ t('items.assistantLabels') }}<input v-model="editItemForm.assistantLabels" :aria-label="t('items.assistantLabels')" :placeholder="t('items.assistantLabels')" /></label>
                  <select v-if="currentItemFields.showRecurrenceRule" v-model="editItemForm.recurrenceRule" aria-label="Recurrence">
                    <option v-for="option in recurrenceOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
                  </select>
                  <button type="submit">{{ t('items.save') }}</button>
                  <button type="button" class="secondary subtle" @click="handleCancelEditItem">{{ t('items.cancel') }}</button>
                </form>
                <span v-else class="item-content">
                  <strong>{{ item.name }}</strong>
                  <small v-if="item.description">{{ item.description }}</small>
                  <a v-if="safeProductUrl(item.url)" :href="safeProductUrl(item.url)!" target="_blank" rel="noopener noreferrer">{{ t('items.viewProduct') }}</a>
                  <small v-if="item.quantity || item.category"><template v-if="item.quantity">{{ item.quantity }}</template><template v-if="item.quantity && item.category"> · </template><template v-if="item.category">{{ item.category }}</template></small>
                  <small v-if="item.ownerLabel || item.assistantLabels"><template v-if="item.ownerLabel">{{ t('items.ownerLabel') }}: {{ item.ownerLabel }}</template><template v-if="item.ownerLabel && item.assistantLabels"> · </template><template v-if="item.assistantLabels">{{ t('items.assistantLabels') }}: {{ item.assistantLabels }}</template></small>
                  <small v-if="item.dueDate">{{ formatDate(item.dueDate) }}</small>
                  <small>{{ t(`items.statuses.${item.status}`) }}<template v-if="item.price != null"> · {{ formatPrice(item.price, item.priceCurrency) }}</template><template v-if="item.lastCompletedAt"> · {{ t('items.lastCompleted') }} {{ formatDate(item.lastCompletedAt) }}</template></small>
                </span>
                <button v-if="!selectedList.archived && !selectedList.template && (selectedList.access === 'OWNER' || selectedList.access === 'CONTRIBUTE') && item.status === 'OPEN' && selectedList.type !== 'WISH'" type="button" class="secondary subtle" :aria-label="`${t('items.done')}: ${item.name}`" @click="handleToggleItemDone(item)">{{ t('items.done') }}</button>
                <button v-else-if="!selectedList.archived && !selectedList.template && (selectedList.access === 'OWNER' || selectedList.access === 'CONTRIBUTE') && item.status === 'DONE'" type="button" class="secondary subtle" :aria-label="`${t('items.reopen')}: ${item.name}`" @click="handleToggleItemDone(item)">{{ t('items.reopen') }}</button>
                <button v-if="!selectedList.archived && !selectedList.template && selectedList.access === 'OWNER' && selectedList.type === 'WISH' && item.status !== 'PURCHASED'" type="button" class="secondary subtle" :aria-label="`${t('items.markPurchased')}: ${item.name}`" @click="handleSetWishStatus(item, 'PURCHASED')">{{ t('items.markPurchased') }}</button>
                <button v-else-if="!selectedList.archived && !selectedList.template && selectedList.access === 'OWNER' && selectedList.type === 'WISH' && item.status === 'PURCHASED'" type="button" class="secondary subtle" :aria-label="`${t('items.reopenWish')}: ${item.name}`" @click="handleSetWishStatus(item, 'OPEN')">{{ t('items.reopenWish') }}</button>
                <button v-if="!selectedList.archived && !selectedList.template && (selectedList.access === 'OWNER' || selectedList.access === 'CONTRIBUTE') && selectedList.type === 'CHORE' && item.recurrenceRule" type="button" class="secondary subtle" :aria-label="`${t('items.skipOccurrence')}: ${item.name}`" @click="handleSkipChore(item)">{{ t('items.skipOccurrence') }}</button>
                <button v-if="!selectedList.archived && !selectedList.template && (selectedList.access === 'OWNER' || selectedList.access === 'CONTRIBUTE') && selectedList.type === 'CHORE' && item.dueDate" type="button" class="secondary subtle" :aria-label="`${t('items.postpone')}: ${item.name}`" @click="handlePostponeChore(item)">{{ t('items.postpone') }}</button>
                <span v-if="item.importStatus === 'PENDING'" role="status">{{ t('items.importPending') }}</span>
                <details v-if="!selectedList.archived && selectedList.access !== 'READ' && editingItemId !== item.id" class="item-actions"><summary :aria-label="`${t('ux.actions')}: ${item.name}`">{{ t('ux.actions') }}</summary><div class="item-actions-menu">
                <button v-if="selectedList.access === 'OWNER' || selectedList.access === 'CONTRIBUTE'" type="button" class="secondary subtle" :aria-label="`${t('items.edit')}: ${item.name}`" @click="handleStartEditItem(item)">{{ t('items.edit') }}</button>

                <button v-if="item.importStatus === 'FAILED'" type="button" class="secondary" @click="handleRetryImport(item)">{{ t('items.retryImport') }}</button>
                <button v-if="selectedList.access === 'OWNER'" type="button" class="danger" :aria-label="`${t('items.delete')}: ${item.name}`" @click="handleDeleteItem(item.id)">{{ t('items.delete') }}</button>
                </div></details>
              </li>
            </ul>
            </section>
          </section>
          <section v-else class="panel empty-state welcome-state">
            <p class="eyebrow">{{ t('ux.welcome') }}</p>
            <h3>{{ t('ux.firstList') }}</h3>
            <p>{{ t('ux.firstListHelp') }}</p>
            <button type="button" @click="openNewList">{{ t('ux.newList') }}</button>
          </section>
        </div>
        </div>
        </div>
      </section>
      </fieldset>
    </section>
  </main>
</template>

<script setup lang="ts">
import { nextTick, computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import AdminPanel from './components/AdminPanel.vue';
import PlanningHub from './components/PlanningHub.vue';
import PrivateImage from './components/PrivateImage.vue';
import { getOverview, getLibrary, getTrashedItems, archiveList, restoreList, restoreItem, saveTemplate, instantiateTemplate, getItem, getList, type Overview, type TrashedItem, type HubView, type AgendaEntry } from './api/client';
import {
  createItem,
  retryItemImport,
  createAdminUser,
  cloneList,
  createList,
  createPublicShare,
  claimPublicItem,
  clearCompletedItems,
  deleteItem,
  deleteList,
  getAdminSettings,
  getAuthSettings,
  getAdminLists,
  getAdminUsers,
  getCurrentUser,
  getItems,
  getListShares,
  getLists,
  getNotifications,
  getPublicShare,
  login,
  logout,
  markNotificationRead,
  register,
  requestMagicLink,
  requestPasswordReset,
  consumeMagicLink,
  consumePasswordReset,
  revokeListShare,
  revokePublicShare,
  scrapeUrl,
  shareListWithUser,
  skipChoreItem,
  postponeChoreItem,
  updateList,
  updateItem,
  updateAdminSettings,
  updateAdminUser,
  ApiClientError,
  type AuthUser,
  type AuthSettings,
  type AdminSettings,
  type AdminListEntry,
  type AdminUserEntry,
  type ItemEntry,
  type ListEntry,
  type ListShareEntry,
  type ListType,
  type NotificationEntry,
  type PublicListEntry,
  type PublicShareMode
} from './api/client';
import { itemFormFieldsForListType, listFormRulesForType } from './listTypes';
import { defaultItemReviewState, reviewDisplayedItems, type ItemReviewState } from './itemReview';

const { locale, t } = useI18n();
const currentUser = ref<AuthUser | null>(null);
const adminView = ref(false);
const hubReady = ref(false);
const hubView = ref<HubView>('lists');
const overview = ref<Overview | null>(null);
const libraryLists = ref<ListEntry[]>([]);
const trashItems = ref<TrashedItem[]>([]);
const savingTemplate = ref(false);
const templateTitle = ref('');
const undoAction = ref<{ label: string; restore: () => Promise<void> } | null>(null);
const listView = ref<'items' | 'sharing' | 'settings'>('items');
const listQuery = ref('');
const newListDisclosure = ref<HTMLDetailsElement | null>(null);
const itemDetailsDisclosure = ref<HTMLDetailsElement | null>(null);
const itemComposerDisclosure = ref<HTMLDetailsElement | null>(null);
const navigationLists = computed(() => lists.value.filter(list => list.title.toLocaleLowerCase().includes(listQuery.value.toLocaleLowerCase())));
async function openNewList() {
  if (newListDisclosure.value) newListDisclosure.value.open = true;
  await nextTick();
  newListDisclosure.value?.querySelector('input')?.focus();
}
function handleMobileListSelection(event: Event) {
  const list = lists.value.find(list => list.id === (event.target as HTMLSelectElement).value);
  if (list) void run(() => selectList(list), false);
}
const lists = ref<ListEntry[]>([]);
const selectedList = ref<ListEntry | null>(null);
const items = ref<ItemEntry[]>([]);
const hideCompletedGroceries = ref(false);
const shares = ref<ListShareEntry[]>([]);
const notifications = ref<NotificationEntry[]>([]);
const authSettings = ref<AuthSettings | null>(null);
const adminSettings = ref<AdminSettings | null>(null);
const adminUsers = ref<AdminUserEntry[]>([]);
const adminLists = ref<AdminListEntry[]>([]);
const publicToken = window.location.pathname.startsWith('/s/') ? decodeURIComponent(window.location.pathname.slice(3)) : '';
const magicToken = window.location.pathname === '/magic-login' ? new URLSearchParams(window.location.search).get('token') : '';
const resetToken = ref(window.location.pathname === '/reset-password' ? new URLSearchParams(window.location.search).get('token') : null);
if (magicToken || resetToken.value) window.history.replaceState({}, '', window.location.pathname);
const publicList = ref<PublicListEntry | null>(null);
const guestName = ref('');
const message = ref('');
const actionPending = ref(false);
const statusElement = ref<HTMLElement | null>(null);
const editingVersion = ref<number>();
let importPoll: number | undefined;
let importPollDeadline = 0;
const messageKind = ref<'status' | 'error'>('status');

const registerForm = reactive({ username: '', email: '', password: '' });
const loginForm = reactive({ username: '', password: '' });
const emailAuthForm = reactive({ email: '', username: '' });
const recoveryEmailInput = ref<HTMLInputElement | null>(null);
const recoveryEmailInvalid = ref(false);

function validateRecoveryEmail() {
  emailAuthForm.email = emailAuthForm.email.trim();
  recoveryEmailInvalid.value = !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailAuthForm.email);
  if (recoveryEmailInvalid.value) recoveryEmailInput.value?.focus();
  return !recoveryEmailInvalid.value;
}
const resetPasswordForm = reactive({ password: '' });
const adminUserForm = reactive<{ username: string; email: string; password: string; role: 'ADMIN' | 'USER' }>({ username: '', email: '', password: '', role: 'USER' });
const listForm = reactive<{ title: string; description: string; type: ListType; targetDate: string }>({ title: '', description: '', type: 'WISH', targetDate: '' });
const editingList = ref(false);
const pendingDeleteListId = ref<string | null>(null);
const editListForm = reactive<{ title: string; description: string; type: ListType; targetDate: string }>({ title: '', description: '', type: 'WISH', targetDate: '' });
const itemForm = reactive({ name: '', description: '', url: '', imageUrl: '', price: undefined as number | undefined, priceCurrency: 'EUR', dueDate: '', recurrenceRule: '', quantity: '', category: '', ownerLabel: '', assistantLabels: '' });
const editingItemId = ref<string | null>(null);
const editItemForm = reactive({ name: '', description: '', url: '', imageUrl: '', price: undefined as number | undefined, priceCurrency: 'EUR', dueDate: '', recurrenceRule: '', quantity: '', category: '', ownerLabel: '', assistantLabels: '' });
const acceptedImageTypes = 'image/png,image/jpeg,image/webp,image/gif';
const acceptedImageTypeSet = new Set(acceptedImageTypes.split(','));
const maxImageFileBytes = 3_500_000;
const itemReviewForm = reactive<ItemReviewState>(defaultItemReviewState());
const itemReviewNow = ref(new Date());
const recurrenceOptions = computed(() => [
  { value: '', label: t('items.noRepeat') },
  { value: 'FREQ=DAILY', label: t('items.daily') },
  { value: 'FREQ=WEEKLY', label: t('items.weekly') },
  { value: 'FREQ=BIWEEKLY', label: t('items.biweekly') },
  { value: 'FREQ=MONTHLY', label: t('items.monthly') },
  { value: 'FREQ=QUARTERLY', label: t('items.quarterly') },
  { value: 'FREQ=ANNUALLY', label: t('items.annually') }
]);
const shareForm = reactive<{ username: string; permission: 'READ' | 'CONTRIBUTE' }>({ username: '', permission: 'READ' });
const publicShareMode = ref<PublicShareMode>('WISH_CLAIM');
const newListRules = computed(() => listFormRulesForType(listForm.type));
const editListRules = computed(() => listFormRulesForType(editListForm.type));
const currentItemFields = computed(() => itemFormFieldsForListType(selectedList.value?.type ?? 'WISH'));
const displayedItems = computed(() => reviewDisplayedItems(
  items.value,
  selectedList.value,
  hideCompletedGroceries.value,
  { ...itemReviewForm, now: itemReviewNow.value }
));
const groceryGroups = computed(() => {
  const grouped = new Map<string, ItemEntry[]>();
  for (const item of displayedItems.value) {
    const category = item.category?.trim() || t('items.uncategorized');
    grouped.set(category, [...(grouped.get(category) ?? []), item]);
  }
  return [...grouped.entries()].sort(([left], [right]) => left.localeCompare(right));
});
let itemReviewNowInterval: number | undefined;

watch(locale, (value) => {
  document.documentElement.lang = value;
  try { localStorage.setItem('listful:language', value); } catch { /* Keep language switching usable without storage. */ }
}, { immediate: true });

function updateItemReviewNow() {
  itemReviewNow.value = new Date();
}

onMounted(async () => {
  updateItemReviewNow();
  itemReviewNowInterval = window.setInterval(updateItemReviewNow, 60_000);
  if (publicToken) {
    await run(async () => {
      publicList.value = await getPublicShare(publicToken);
    }, false);
    return;
  }

  if (magicToken) {
    await run(async () => {
      currentUser.value = await consumeMagicLink({ token: magicToken });
      await loadLists();
      await loadNotifications();
      await maybeLoadAdminPanel();
      window.history.replaceState({}, '', '/');
    }, false);
    return;
  }

  await run(async () => {
    currentUser.value = await getCurrentUser();
    if (!currentUser.value) {
      await loadAuthSettings();
      return;
    }
    await loadLists();
    await loadNotifications();
    await maybeLoadAdminPanel();
  }, false);
});

onUnmounted(() => {
  window.clearTimeout(importPoll);
  if (itemReviewNowInterval !== undefined) {
    window.clearInterval(itemReviewNowInterval);
  }
});

async function handleRegister() {
  await run(async () => {
    currentUser.value = await register(registerForm);
    registerForm.password = '';
    await loadLists();
    await loadNotifications();
    await maybeLoadAdminPanel();
  });
}

async function handleLogin() {
  await run(async () => {
    currentUser.value = await login(loginForm);
    loginForm.password = '';
    await loadLists();
    await loadNotifications();
    await maybeLoadAdminPanel();
  });
}

async function handleRequestMagicLink() {
  if (!validateRecoveryEmail()) return;
  await run(async () => {
    await requestMagicLink({ email: emailAuthForm.email, username: emailAuthForm.username.trim() || undefined });
    message.value = t('auth.emailSent');
  });
}

async function handleRequestPasswordReset() {
  if (!validateRecoveryEmail()) return;
  await run(async () => {
    await requestPasswordReset({ email: emailAuthForm.email, username: emailAuthForm.username.trim() || undefined });
    message.value = t('auth.emailSent');
  });
}

async function handleConsumePasswordReset() {
  const token = resetToken.value;
  if (!token) return;
  await run(async () => {
    await consumePasswordReset({ token, password: resetPasswordForm.password });
    resetPasswordForm.password = '';
    message.value = t('auth.passwordUpdated');
    window.history.replaceState({}, '', '/');
    resetToken.value = null;
  });
}

async function handleLogout() {
  hubView.value = 'lists';
  undoAction.value = null;
  libraryLists.value = [];
  trashItems.value = [];
  overview.value = null;
  adminView.value = false;
  listQuery.value = '';
  listView.value = 'items';
  await logout();
  window.clearTimeout(importPoll);
  resetItemForm();
  resetItemReviewForm();
  Object.assign(editItemForm, itemForm);
  Object.assign(listForm, { title: '', description: '', type: 'WISH', targetDate: '' });
  Object.assign(editListForm, listForm);
  Object.assign(adminUserForm, { username: '', email: '', password: '', role: 'USER' });
  Object.assign(registerForm, { username: '', email: '', password: '' });
  Object.assign(loginForm, { username: '', password: '' });
  Object.assign(emailAuthForm, { username: '', email: '' });
  Object.assign(shareForm, { username: '', permission: 'READ' });
  resetPasswordForm.password = '';
  templateTitle.value = '';
  editingItemId.value = null;
  editingList.value = false;
  savingTemplate.value = false;
  message.value = '';
  currentUser.value = null;
  lists.value = [];
  selectedList.value = null;
  items.value = [];
  shares.value = [];
  notifications.value = [];
  adminSettings.value = null;
  adminUsers.value = [];
  adminLists.value = [];
  await loadAuthSettings();
}

async function loadAuthSettings() {
  try {
    authSettings.value = await getAuthSettings();
  } catch {
    authSettings.value = { registrationAvailable: false };
  }
}

async function loadLists() {
  listView.value = 'items';
  editingItemId.value = null;
  resetItemForm();
  lists.value = await getLists();
  let rememberedList: string | null = null;
  try { rememberedList = localStorage.getItem(`listful:last-list:${currentUser.value?.id}`); } catch { /* Storage can be unavailable in private browsing. */ }
  selectedList.value = lists.value.find(list => list.id === rememberedList) ?? lists.value[0] ?? null;
  if (selectedList.value) {
    publicShareMode.value = selectedList.value.publicShareMode ?? (selectedList.value.type === 'WISH' ? 'WISH_CLAIM' : 'VIEW');
  }
  resetItemReviewForm();
  updateItemReviewNow();
  await loadListDetails();
}

async function selectList(list: ListEntry) {
  savingTemplate.value = false;
  listView.value = 'items';
  if (itemDetailsDisclosure.value) itemDetailsDisclosure.value.open = false;
  items.value = [];
  shares.value = [];
  editingItemId.value = null;
  resetItemForm();
  window.clearTimeout(importPoll);
  importPollDeadline = Date.now() + 120_000;
  selectedList.value = list;
  try { if (!list.template && !list.archived) localStorage.setItem(`listful:last-list:${currentUser.value?.id}`, list.id); } catch { /* Navigation also works without browser storage. */ }
  publicShareMode.value = list.publicShareMode ?? (list.type === 'WISH' ? 'WISH_CLAIM' : 'VIEW');
  editingList.value = false;
  pendingDeleteListId.value = null;
  resetItemReviewForm();
  updateItemReviewNow();
  await loadListDetails();
}

function resetItemReviewForm() {
  Object.assign(itemReviewForm, defaultItemReviewState());
}

async function loadListDetails() {
  const listId = selectedList.value?.id;
  if (!listId) {
    items.value = [];
    shares.value = [];
    return;
  }
  await Promise.all([loadItems(listId), selectedList.value?.access === 'OWNER' ? loadShares(listId) : Promise.resolve(shares.value = [])]);
}

async function handleCreateList() {
  await run(async () => {
    const created = await createList({
      title: listForm.title,
      description: listForm.description || undefined,
      type: listForm.type,
      targetDate: listForm.type === 'EVENT' ? toIsoInstant(listForm.targetDate) : undefined
    });
    listForm.title = '';
    listForm.description = '';
    listForm.targetDate = '';
    lists.value = [created, ...lists.value];
    await selectList(created);
    if (newListDisclosure.value) newListDisclosure.value.open = false;
    message.value = t('ux.listCreated');
  });
}

function handleStartEditList() {
  if (!selectedList.value) return;
  editingList.value = true;
  pendingDeleteListId.value = null;
  editListForm.title = selectedList.value.title;
  editListForm.description = selectedList.value.description ?? '';
  editListForm.type = selectedList.value.type;
  editListForm.targetDate = toLocalDateTime(selectedList.value.targetDate);
}

function handleCancelEditList() {
  editingList.value = false;
}

async function handleSaveEditedList() {
  if (!selectedList.value) return;
  await run(async () => {
    const updated = await updateList(selectedList.value!.id, {
      title: editListForm.title,
      description: editListForm.description || undefined,
      type: editListForm.type,
      targetDate: editListForm.type === 'EVENT' ? toIsoInstant(editListForm.targetDate) : undefined
    });
    selectedList.value = updated;
    publicShareMode.value = updated.publicShareMode ?? (updated.type === 'WISH' ? 'WISH_CLAIM' : 'VIEW');
    lists.value = lists.value.map((list) => list.id === updated.id ? updated : list);
    editingList.value = false;
    await loadListDetails();
  });
}

async function handleCloneList() {
  if (!selectedList.value) return;
  await run(async () => {
    const cloned = await cloneList(selectedList.value!.id, { title: `${selectedList.value!.title} copy` });
    lists.value = [cloned, ...lists.value];
    await selectList(cloned);
  });
}

function handleRequestDeleteList(id: string) {
  pendingDeleteListId.value = id;
}

async function handleConfirmDeleteList(id: string) {
  await run(async () => {
    const title = selectedList.value?.title ?? '';
    await deleteList(id);
    undoAction.value = { label: t('planning.movedToTrash', { name: title }), restore: async () => { await restoreList(id); } };
    pendingDeleteListId.value = null;
    editingList.value = false;
    await loadLists();
  });
}

async function loadItems(listId = selectedList.value?.id) {
  if (!listId) {
    items.value = [];
    return;
  }
  const loadedItems = await getItems(listId);
  if (selectedList.value?.id === listId) {
    items.value = loadedItems;
    if (loadedItems.some(item => item.importStatus === 'PENDING')) scheduleImportPoll(listId);

  }
}

async function loadShares(listId = selectedList.value?.id) {
  if (!listId) {
    shares.value = [];
    return;
  }
  const loadedShares = await getListShares(listId);
  if (selectedList.value?.id === listId) {
    shares.value = loadedShares;
  }
}

async function loadNotifications() {
  notifications.value = await getNotifications();
}

async function maybeLoadAdminPanel() {
  if (currentUser.value?.role === 'ADMIN') {
    await handleLoadAdminPanel();
  } else {
    adminSettings.value = null;
    adminUsers.value = [];
    adminLists.value = [];
  }
}

async function handleLoadAdminPanel() {
  const [settings, users, allLists] = await Promise.all([getAdminSettings(), getAdminUsers(), getAdminLists()]);
  adminSettings.value = settings;
  adminUsers.value = users;
  adminLists.value = allLists;
}

async function handleToggleRegistration(registrationEnabled: boolean) {
  await run(async () => {
    adminSettings.value = await updateAdminSettings({ registrationEnabled });
    adminUsers.value = await getAdminUsers();
  });
}

async function handleAdminCreateUser() {
  await run(async () => {
    await createAdminUser({ ...adminUserForm, email: adminUserForm.email || undefined });
    adminUserForm.username = '';
    adminUserForm.email = '';
    adminUserForm.password = '';
    adminUserForm.role = 'USER';
    await handleLoadAdminPanel();
  });
}

async function handleToggleUserActive(id: string, active: boolean) {
  await run(async () => {
    const updated = await updateAdminUser(id, { active });
    adminUsers.value = adminUsers.value.map((user) => user.id === id ? updated : user);
  });
}

async function handleMarkNotificationRead(notificationId: string) {
  await run(async () => {
    await markNotificationRead(notificationId);
    notifications.value = notifications.value.filter((notification) => notification.id !== notificationId);
  });
}

async function handleShareList() {
  if (!selectedList.value) return;
  await run(async () => {
    const share = await shareListWithUser(selectedList.value!.id, { username: shareForm.username, permission: shareForm.permission });
    shareForm.username = '';
    shareForm.permission = 'READ';
    shares.value = [share, ...shares.value.filter((existing) => existing.userId !== share.userId)];
  });
}

async function handleRevokeShare(username: string) {
  if (!selectedList.value) return;
  await run(async () => {
    await revokeListShare(selectedList.value!.id, username);
    shares.value = shares.value.filter((share) => share.username !== username);
  });
}

async function handleCreatePublicShare() {
  if (!selectedList.value) return;
  if (selectedList.value.publicList && !window.confirm(t('sharing.replaceWarning'))) return;
  await run(async () => {
    const token = await createPublicShare(selectedList.value!.id, publicShareMode.value);
    selectedList.value = { ...selectedList.value!, publicList: token.publicList, shareToken: token.shareToken, publicShareMode: token.mode };
    lists.value = lists.value.map((list) => list.id === selectedList.value!.id ? selectedList.value! : list);
  });
}

async function handleRevokePublicShare() {
  if (!selectedList.value) return;
  await run(async () => {
    await revokePublicShare(selectedList.value!.id);
    const defaultMode = selectedList.value!.type === 'WISH' ? 'WISH_CLAIM' : 'VIEW';
    publicShareMode.value = defaultMode;
    selectedList.value = { ...selectedList.value!, publicList: false, shareToken: null, publicShareMode: defaultMode };
    lists.value = lists.value.map((list) => list.id === selectedList.value!.id ? selectedList.value! : list);
  });
}

async function handleClaimPublicItem(itemId: string) {
  if (!publicToken) return;
  await run(async () => {
    await claimPublicItem(publicToken, itemId, { guestName: guestName.value });
    guestName.value = '';
    publicList.value = await getPublicShare(publicToken);
  });
}

function publicShareUrl(token: string) {
  return `${window.location.origin}/s/${token}`;
}

async function handleScrapeItemUrl() {
  if (!itemForm.url) return;
  await run(async () => {
    const scraped = await scrapeUrl({ url: itemForm.url });
    if (!itemForm.name && scraped.title) {
      itemForm.name = scraped.title;
    }
    if (!itemForm.description && scraped.description) {
      itemForm.description = scraped.description;
    }
    if (scraped.imageUrl) {
      itemForm.imageUrl = scraped.imageUrl;
    }
    if (scraped.price !== null) {
      itemForm.price = scraped.price;
      itemForm.priceCurrency = scraped.priceCurrency ?? '';
    }
  });
}

type ImageForm = { imageUrl: string };

function setImageFromFile(file: File | undefined, form: ImageForm) {
  if (!file || !acceptedImageTypeSet.has(file.type)) return;
  if (file.size > maxImageFileBytes) {
    messageKind.value = 'error';
    message.value = t('items.imageTooLarge');
    return;
  }
  const reader = new FileReader();
  reader.addEventListener('load', () => {
    if (typeof reader.result === 'string') form.imageUrl = reader.result;
  });
  reader.readAsDataURL(file);
}

function handleImagePaste(event: ClipboardEvent, form: ImageForm) {
  const file = Array.from(event.clipboardData?.files ?? []).find((candidate) => acceptedImageTypeSet.has(candidate.type));
  if (!file) return;
  event.preventDefault();
  setImageFromFile(file, form);
}

function handleImageFileInput(event: Event, form: ImageForm) {
  const input = event.target as HTMLInputElement;
  setImageFromFile(input.files?.[0], form);
  input.value = '';
}

async function handleCreateItem() {
  if (!selectedList.value) return;
  await run(async () => {
    const fields = currentItemFields.value;
    const created = await createItem(selectedList.value!.id, {
      name: itemForm.name || undefined,
      description: itemForm.description || undefined,
      url: fields.showUrl ? itemForm.url || undefined : undefined,
      imageUrl: fields.showImageUrl ? itemForm.imageUrl || undefined : undefined,
      price: fields.showPrice ? itemForm.price : undefined,
      priceCurrency: fields.showPrice ? itemForm.priceCurrency || undefined : undefined,
      dueDate: fields.showDueDate ? toIsoInstant(itemForm.dueDate) : undefined,
      recurrenceRule: fields.showRecurrenceRule ? itemForm.recurrenceRule || undefined : undefined,
      quantity: fields.showQuantity ? itemForm.quantity || undefined : undefined,
      category: fields.showCategory ? itemForm.category || undefined : undefined,
      ownerLabel: fields.showResponsibility ? itemForm.ownerLabel || undefined : undefined,
      assistantLabels: fields.showResponsibility ? itemForm.assistantLabels || undefined : undefined
    });
    resetItemForm();
    items.value = [created, ...items.value];
    if (itemDetailsDisclosure.value) itemDetailsDisclosure.value.open = false;
    if (itemComposerDisclosure.value) itemComposerDisclosure.value.open = false;
    message.value = t('ux.itemAdded');
    await nextTick();
    itemComposerDisclosure.value?.querySelector('summary')?.focus();
    if (created.importStatus === 'PENDING' || created.name === 'Loading metadata…') {
      importPollDeadline = Date.now() + 120_000;
      scheduleImportPoll(created.listId);
    }
  });
}

function handleStartEditItem(item: ItemEntry) {
  editingItemId.value = item.id;
  editingVersion.value = item.version;
  editItemForm.name = item.name;
  editItemForm.description = item.description ?? '';
  editItemForm.url = item.url ?? '';
  editItemForm.imageUrl = item.imageUrl ?? '';
  editItemForm.price = item.price ?? undefined;
  editItemForm.priceCurrency = item.priceCurrency ?? '';
  editItemForm.dueDate = toLocalDateTime(item.dueDate);
  editItemForm.recurrenceRule = item.recurrenceRule ?? '';
  editItemForm.quantity = item.quantity ?? '';
  editItemForm.category = item.category ?? '';
  editItemForm.ownerLabel = item.ownerLabel ?? '';
  editItemForm.assistantLabels = item.assistantLabels ?? '';
}

function handleCancelEditItem() {
  editingItemId.value = null;
}

async function handleSaveEditedItem(item: ItemEntry) {
  await run(async () => {
    const updated = await updateItem(item.id, itemPayloadFromEditForm(item.status));
    items.value = items.value.map((existing) => existing.id === item.id ? updated : existing);
    editingItemId.value = null;
  });
}

async function handleToggleItemDone(item: ItemEntry) {
  const nextStatus = item.status === 'DONE' ? 'OPEN' : 'DONE';
  await run(async () => {
    const updated = await updateItem(item.id, itemPayloadFromItem(item, nextStatus));
    items.value = items.value.map((existing) => existing.id === item.id ? updated : existing);
  });
}

async function handleSetWishStatus(item: ItemEntry, status: 'OPEN' | 'PURCHASED') {
  await run(async () => {
    const updated = await updateItem(item.id, itemPayloadFromItem(item, status));
    items.value = items.value.map((existing) => existing.id === item.id ? updated : existing);
  });
}

async function handleSkipChore(item: ItemEntry) {
  await run(async () => {
    const updated = await skipChoreItem(item.id);
    items.value = items.value.map((existing) => existing.id === item.id ? updated : existing);
  });
}

async function handlePostponeChore(item: ItemEntry) {
  await run(async () => {
    const updated = await postponeChoreItem(item.id, { days: 1 });
    items.value = items.value.map((existing) => existing.id === item.id ? updated : existing);
  });
}

async function handleDeleteItem(id: string) {
  if (!window.confirm(t('items.deleteWarning'))) return;
  await run(async () => {
    const name = items.value.find(item => item.id === id)?.name ?? '';
    await deleteItem(id);
    undoAction.value = { label: t('planning.movedToTrash', { name }), restore: async () => { await restoreItem(id); } };
    items.value = items.value.filter((item) => item.id !== id);
  });
}

async function handleClearCompletedGroceries() {
  if (!selectedList.value || !window.confirm(t('items.clearWarning'))) return;
  await run(async () => {
    const removed = items.value.filter(item => item.status === 'DONE').map(item => item.id);
    await clearCompletedItems(selectedList.value!.id);
    if (removed.length) undoAction.value = { label: t('planning.itemsTrashed', { count: removed.length }), restore: async () => { for (const id of removed) await restoreItem(id); } };
    items.value = items.value.filter((item) => item.status !== 'DONE');
  });
}

function itemPayloadFromEditForm(status: ItemEntry['status']) {
  const fields = currentItemFields.value;
  return {
    version: editingVersion.value,
    name: editItemForm.name,
    description: editItemForm.description || undefined,
    url: fields.showUrl ? editItemForm.url || undefined : undefined,
    imageUrl: fields.showImageUrl ? editItemForm.imageUrl || undefined : undefined,
    price: fields.showPrice ? editItemForm.price : undefined,
    priceCurrency: fields.showPrice ? editItemForm.priceCurrency || undefined : undefined,
    status,
    dueDate: fields.showDueDate ? toIsoInstant(editItemForm.dueDate) : undefined,
    recurrenceRule: fields.showRecurrenceRule ? editItemForm.recurrenceRule || undefined : undefined,
    quantity: fields.showQuantity ? editItemForm.quantity || undefined : undefined,
    category: fields.showCategory ? editItemForm.category || undefined : undefined,
    ownerLabel: fields.showResponsibility ? editItemForm.ownerLabel || undefined : undefined,
    assistantLabels: fields.showResponsibility ? editItemForm.assistantLabels || undefined : undefined
  };
}

function itemPayloadFromItem(item: ItemEntry, status: ItemEntry['status']) {
  return {
    version: item.version,
    priceCurrency: item.priceCurrency,
    name: item.name,
    description: item.description ?? undefined,
    url: item.url ?? undefined,
    imageUrl: item.imageUrl ?? undefined,
    price: item.price ?? undefined,
    status,
    dueDate: item.dueDate ?? undefined,
    recurrenceRule: item.recurrenceRule ?? undefined,
    quantity: item.quantity ?? undefined,
    category: item.category ?? undefined,
    ownerLabel: item.ownerLabel ?? undefined,
    assistantLabels: item.assistantLabels ?? undefined
  };
}

async function run(action: () => Promise<void>, blocking = true) {
  if (blocking && actionPending.value) return;
  if (blocking) actionPending.value = true;
  message.value = '';
  messageKind.value = 'status';
  try {
    await action();
  } catch (error) {
    messageKind.value = 'error';
    message.value = localizedErrorMessage(error);
    if (error instanceof ApiClientError && error.status === 401) {
      currentUser.value = null;
      await loadAuthSettings();
    }
    await nextTick();
    statusElement.value?.focus();
  } finally {
    if (blocking) actionPending.value = false;
  }
}

function localizedErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError) {
    const key = `errors.${error.code}`;
    const translated = t(key);
    return translated === key ? t('errors.request_failed') : translated;
  }
  return error instanceof Error ? error.message : String(error);
}

function toIsoInstant(localDateTime: string): string | undefined {
  return localDateTime ? new Date(localDateTime).toISOString() : undefined;
}

function toLocalDateTime(isoInstant: string | null): string {
  if (!isoInstant) return '';
  const date = new Date(isoInstant);
  const offsetMs = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16);
}

function safeProductUrl(value: string | null): string | null {
  if (!value) return null;
  try {
    const parsed = new URL(value);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed.href : null;
  } catch {
    return null;
  }
}

async function copyShareLink(token: string) {
  await navigator.clipboard.writeText(publicShareUrl(token));
  message.value = t('sharing.copied');
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

function formatPrice(value: number, currency?: string | null) {
  return currency && /^[A-Z]{3}$/.test(currency)
    ? new Intl.NumberFormat(locale.value, { style: 'currency', currency }).format(value)
    : new Intl.NumberFormat(locale.value).format(value);
}

function scheduleImportPoll(listId: string) {
  window.clearTimeout(importPoll);
  if (!importPollDeadline) importPollDeadline = Date.now() + 120_000;
  if (Date.now() >= importPollDeadline) return;
  importPoll = window.setTimeout(async () => {
    if (selectedList.value?.id !== listId) return;
    try { await loadItems(listId); }
    catch { messageKind.value = 'error'; message.value = t('errors.network_error'); }
  }, 2000);
}

async function handleRetryImport(item: ItemEntry) {
  await run(async () => {
    const updated = await retryItemImport(item.id);
    items.value = items.value.map(existing => existing.id === item.id ? updated : existing);
    importPollDeadline = Date.now() + 120_000;
    scheduleImportPoll(item.listId);
  });
}

function resetItemForm() {
  itemForm.name = '';
  itemForm.description = '';
  itemForm.url = '';
  itemForm.imageUrl = '';
  itemForm.price = undefined;
  itemForm.priceCurrency = 'EUR';
  itemForm.dueDate = '';
  itemForm.recurrenceRule = '';
  itemForm.quantity = '';
  itemForm.category = '';
  itemForm.ownerLabel = '';
  itemForm.assistantLabels = '';
}
async function loadHub(view = hubView.value) {
  if (view === 'today') overview.value = await getOverview(Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC');
  else if (view !== 'lists') {
    libraryLists.value = await getLibrary(view);
    trashItems.value = view === 'trash' ? await getTrashedItems() : [];
  }
  hubReady.value = true;
}
async function showHub(view: HubView) {
  if (actionPending.value) return;
  const refreshLists = view === 'lists' && (hubView.value !== 'lists' || selectedList.value?.template || selectedList.value?.archived);
  hubView.value = view;
  hubReady.value = false;
  if (refreshLists) await run(loadLists);
  else if (view !== 'lists') { libraryLists.value = []; trashItems.value = []; await run(() => loadHub(view)); }
}
async function openHubList(id: string) {
  await run(async () => {
    const list = lists.value.find(list => list.id === id) ?? await getList(id);
    await selectList(list);
    hubView.value = 'lists';
  });
}
async function completeAgenda(entry: AgendaEntry) {
  await run(async () => {
    const item = await getItem(entry.id);
    // Compare the overview revision too: do not complete a task silently edited since it was shown.
    if (item.version !== entry.version) throw new ApiClientError(409, 'stale_item', 'Refresh Today before completing this item.');
    await updateItem(item.id, itemPayloadFromItem(item, entry.listType === 'WISH' ? 'PURCHASED' : 'DONE'));
    await loadHub('today');
  });
}
async function handleArchiveSelected() {
  if (!selectedList.value) return;
  await run(async () => {
    const list = selectedList.value!;
    await archiveList(list.id, true);
    undoAction.value = { label: t('planning.listArchived', { name: list.title }), restore: async () => { await archiveList(list.id, false); } };
    await loadLists();
  });
}
async function handleUndo() {
  const action = undoAction.value;
  if (!action) return;
  await run(async () => {
    await action.restore();
    undoAction.value = null;
    await loadLists();
    if (hubView.value !== 'lists') await loadHub();
    message.value = t('planning.restored');
  });
}
async function handleRestoreList(list: ListEntry) {
  await run(async () => {
    const restored = list.deletedAt ? await restoreList(list.id) : await archiveList(list.id, false);
    await loadLists();
    await selectList(restored);
    hubView.value = 'lists';
    undoAction.value = null;
    message.value = t('planning.restored');
  });
}
async function handleRestoreItem(id: string) {
  await run(async () => { await restoreItem(id); await loadHub('trash'); undoAction.value = null; message.value = t('planning.restored'); });
}
async function handleSaveTemplate() {
  if (!selectedList.value) return;
  await run(async () => {
    await saveTemplate(selectedList.value!.id, templateTitle.value);
    savingTemplate.value = false;
    hubView.value = 'templates';
    await loadHub('templates');
    message.value = t('planning.templateSaved');
  });
}
async function handleInstantiateTemplate(id: string, title: string, targetDate?: string) {
  await run(async () => {
    const list = await instantiateTemplate(id, title, targetDate);
    await loadLists();
    await selectList(list);
    hubView.value = 'lists';
    message.value = t('planning.createdFromTemplate');
  });
}
async function handleDeleteTemplate(id: string) {
  if (!window.confirm(t('planning.deleteTemplateConfirm'))) return;
  await run(async () => {
    await deleteList(id);
    undoAction.value = { label: t('planning.templateTrashed'), restore: async () => { await restoreList(id); } };
    await loadHub('templates');
  });
}
</script>
