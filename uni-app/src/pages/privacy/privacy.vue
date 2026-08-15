<template>
  <view class="text-page">
    <rich-text class="content" :nodes="content" />
  </view>
</template>

<script setup lang="ts">
const content = `<h2>隐私政策</h2>
<p>最后更新日期：2025 年 3 月 20 日</p>
<p>我们非常重视您的隐私。本隐私政策详细说明了我们如何收集、使用和保护您的个人信息。</p>
<h3>1. 信息收集</h3>
<p>我们收集的信息包括：手机号、邮箱地址、设备信息、使用记录等。</p>
<h3>2. 信息使用</h3>
<p>收集的信息将用于：提供服务、账户管理、安全防护、服务改进。</p>
<h3>3. 信息共享</h3>
<p>我们不会将您的个人信息出售给第三方。仅在法律法规要求时可能披露。</p>
<h3>4. 信息安全</h3>
<p>我们采用行业标准的安全措施保护您的数据，包括加密传输和存储。</p>
<h3>5. 用户权利</h3>
<p>您有权访问、更正或删除您的个人信息，可通过应用内的账户设置进行操作。</p>
<h3>6. 联系方式</h3>
<p>如有隐私相关问题，请通过应用内的联系方式与我们取得联系。</p>`
</script>

<style scoped>
.text-page { min-height: 100vh; background: #fff; padding: 16px; }
.content { font-size: 14px; color: #333; line-height: 1.8; }
</style>
