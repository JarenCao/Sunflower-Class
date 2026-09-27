<!-- 订单占位页：订单创建、支付及结果查询尚未接入，不发起扣款。 -->
<template>
  <div class="inner-banner">
    <div class="container">
      <span class="section-kicker">MY ORDERS</span>
      <h1>我的订单</h1>
      <p>每一份学习投资，都值得被认真记录。</p>
    </div>
  </div>
  <div class="container inner-content">
    <div class="empty-order">
      <span>▧</span>
      <h2>订单功能正在准备中</h2>
      <p>订单创建、支付二维码和支付结果接口尚未接入。这里不会创建真实订单或发起扣款。</p>
      <RouterLink
        to="/courses"
        class="dark-button"
      >
        先去看看课程 →
      </RouterLink>
    </div>
  </div>
</template>
