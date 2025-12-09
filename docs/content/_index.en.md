---
title: Upvy
layout: hextra-home
---

<main class="home-wrapper" style="padding-left: max(4rem, env(safe-area-inset-left)); padding-right: max(4rem, env(safe-area-inset-right));">

<div style="display: grid; grid-template-columns: 1fr 320px; gap: 4rem; align-items: center; margin-bottom: 3rem;">
  <div>
    {{< hextra/hero-badge >}}
      <div class="hx:w-2 hx:h-2 hx:rounded-full hx:bg-primary-400"></div>
      <span>Beta Coming Soon</span>
    {{< /hextra/hero-badge >}}

    <div class="hx:mt-8 hx:mb-2">
    {{< hextra/hero-headline >}}
      Turn Scroll Time&nbsp;<br class="hx:sm:block hx:hidden" />into Growth Time
    {{< /hextra/hero-headline >}}
    </div>

    <div class="hx:mb-8">
    {{< hextra/hero-subtitle >}}
      A short-form learning platform where you grow naturally while scrolling.<br />
      Experience natural growth, not burdensome learning.
    {{< /hextra/hero-subtitle >}}
    </div>

    <div style="display: flex; flex-direction: column; gap: 0.75rem; margin-bottom: 1rem; max-width: 400px;">
      {{< hextra/hero-button text="Download on App Store" link="https://apps.apple.com/app/upvy" >}}
      {{< hextra/hero-button text="Get it on Google Play" link="https://play.google.com/store/apps/details?id=com.upvy" >}}
    </div>
    <p class="hx:text-sm hx:text-gray-500">iOS 14.0+ | Android 8.0+</p>
  </div>

  <div style="display: flex; justify-content: center; align-items: center;">
    <img src="/images/mascot.png" alt="Upvy Mascot" style="width: 100%; max-width: 300px; border-radius: 20px;" />
  </div>
</div>

<style>
@media (max-width: 768px) {
  div[style*="grid-template-columns: 1fr 320px"] {
    grid-template-columns: 1fr !important;
  }
}
</style>

<div class="hx:mt-16 hx:mb-6">
<h2 class="hx:text-2xl hx:font-bold">Key Features</h2>
</div>

{{< hextra/feature-grid >}}
  {{< hextra/feature-card
    title="Smart Feed"
    subtitle="AI-powered recommendation algorithm provides content tailored to your interests"
    icon="sparkles"
  >}}
  {{< hextra/feature-card
    title="Diverse Categories"
    subtitle="Programming, Design, Language, Business, Motivation and more"
    icon="book-open"
  >}}
  {{< hextra/feature-card
    title="Creator Studio"
    subtitle="Anyone can easily create content and share knowledge"
    icon="video-camera"
  >}}
  {{< hextra/feature-card
    title="Social Interaction"
    subtitle="Grow together with the community through likes, comments, saves, and shares"
    icon="chat"
  >}}
  {{< hextra/feature-card
    title="Multi-language Support"
    subtitle="Connect with users worldwide in Korean, English, and Japanese"
    icon="globe-alt"
  >}}
  {{< hextra/feature-card
    title="Analytics"
    subtitle="Detailed statistics and insights for creators"
    icon="chart-bar"
  >}}
{{< /hextra/feature-grid >}}

<div class="hx:mt-16 hx:mb-6">
<h2 class="hx:text-2xl hx:font-bold">Why Upvy?</h2>
</div>

{{< hextra/feature-grid >}}
  {{< hextra/feature-card
    title="Guilt-Free Scrolling"
    subtitle="Enjoy scrolling while gaining something valuable. As fun as TikTok, but you'll find yourself learning something new before you know it."
    icon="heart"
  >}}
  {{< hextra/feature-card
    title="Natural Growth"
    subtitle="Learn naturally without feeling like you're studying. Interesting insights, not rigid education."
    icon="academic-cap"
  >}}
  {{< hextra/feature-card
    title="Learning in Daily Life"
    subtitle="During commutes and breaks, without pressure. Meaningful learning is possible even in short time periods."
    icon="clock"
  >}}
  {{< hextra/feature-card
    title="Meaningful Time"
    subtitle="From \"wasted time again\" to \"oh, I didn't know that!\". Not investing time, but utilizing it."
    icon="fire"
  >}}
{{< /hextra/feature-grid >}}

<div class="hx:mt-16 hx:mb-8 hx:text-center">
<h2 class="hx:text-2xl hx:font-bold hx:mb-6">Get Started Now</h2>
<div class="hx:flex hx:gap-4 hx:justify-center hx:flex-wrap">
{{< hextra/hero-button text="App Store" link="https://apps.apple.com/app/upvy" >}}
{{< hextra/hero-button text="Google Play" link="https://play.google.com/store/apps/details?id=com.upvy" >}}
</div>
</div>

</main>
