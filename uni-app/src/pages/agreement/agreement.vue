<template>
  <view class="text-page">
    <rich-text class="content" :nodes="content" />
  </view>
</template>

<script setup lang="ts">
const content = `<h2>用户协议</h2>
<p>最后更新日期：2025 年 3 月 20 日</p>
<p>欢迎使用 AI 助手。本协议是您与本应用之间关于使用服务的法律协议。</p>
<h3>1. 服务说明</h3>
<p>本应用提供人工智能辅助对话服务，帮助用户完成文本生成、信息查询、文档处理等任务。</p>
<h3>2. 用户行为规范</h3>
<p>用户不得利用本服务从事违反法律法规、损害他人合法权益的活动。</p>
<h3>3. 知识产权</h3>
<p>本应用的所有内容（包括但不限于文字、图片、代码、界面设计）的知识产权归本应用所有。</p>
<h3>4. 免责声明</h3>
<p>AI 生成的内容仅供参考，不构成专业建议。用户应自行判断内容的准确性和适用性。</p>
<h3>5. 协议修改</h3>
<p>本应用有权根据需要修改本协议，修改后的协议将在应用内公示。</p>`
</script>

<style scoped>
.text-page { min-height: 100vh; background: #fff; padding: 16px; }
.content { font-size: 14px; color: #333; line-height: 1.8; }
</style>
