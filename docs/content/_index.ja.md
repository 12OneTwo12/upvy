---
title: Upvy
layout: hextra-home
---

<main class="home-wrapper">

<div class="hero-grid">
<div class="hero-content">

{{< hextra/hero-badge >}}
  <div class="hx:w-2 hx:h-2 hx:rounded-full hx:bg-primary-400"></div>
  <span>iOS版リリース完了 / Android版準備中</span>
{{< /hextra/hero-badge >}}

<div class="hx:mt-8 hx:mb-2">
{{< hextra/hero-headline >}}
  スクロール時間を&nbsp;<br class="hx:sm:block hx:hidden" />成長時間に
{{< /hextra/hero-headline >}}
</div>

<div class="hx:mb-8">
{{< hextra/hero-subtitle >}}
  楽しくスクロールしながら自然に学ぶショートフォーム学習プラットフォーム。<br />
  負担のある学習ではなく、自然な成長を体験してください。
{{< /hextra/hero-subtitle >}}
</div>

<div class="button-container">
{{< hextra/hero-button text="App Storeでダウンロード" link="https://apps.apple.com/app/upvy" >}}
{{< hextra/hero-button text="Google Playでダウンロード" link="https://play.google.com/store/apps/details?id=com.upvy" >}}
</div>

<div class="hx:mt-4">
<p class="hx:text-sm hx:text-gray-500">iOS 14.0+ | Android 8.0+</p>
</div>

</div>

<div class="hero-mascot">
  <img src="/images/mascot.png" alt="Upvy Mascot" />
</div>
</div>

<style>
.home-wrapper {
  padding-left: max(4rem, env(safe-area-inset-left));
  padding-right: max(4rem, env(safe-area-inset-right));
}
.hero-grid {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 4rem;
  align-items: center;
  margin-bottom: 3rem;
}
.hero-content {
  max-width: 600px;
}
.hero-mascot {
  display: flex;
  justify-content: center;
  align-items: center;
}
.hero-mascot img {
  width: 100%;
  max-width: 280px;
  border-radius: 20px;
}
.button-container {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  max-width: 400px;
  margin-bottom: 1rem;
}
@media (max-width: 768px) {
  .home-wrapper {
    padding-left: max(1.5rem, env(safe-area-inset-left));
    padding-right: max(1.5rem, env(safe-area-inset-right));
  }
  .hero-grid {
    grid-template-columns: 1fr;
    gap: 2rem;
  }
}
@media (min-width: 769px) and (max-width: 1024px) {
  .home-wrapper {
    padding-left: max(2.5rem, env(safe-area-inset-left));
    padding-right: max(2.5rem, env(safe-area-inset-right));
  }
  .hero-grid {
    grid-template-columns: 1fr;
    gap: 2rem;
  }
}
</style>

<div class="hx:mt-16 hx:mb-6">
<h2 class="hx:text-2xl hx:font-bold">主な機能</h2>
</div>

{{< hextra/feature-grid >}}
  {{< hextra/feature-card
    title="スマートフィード"
    subtitle="AIベースのレコメンドアルゴリズムがあなたの興味に合ったコンテンツを提供します"
    icon="sparkles"
  >}}
  {{< hextra/feature-card
    title="多様なカテゴリ"
    subtitle="プログラミング、デザイン、言語、ビジネス、モチベーションなど様々なテーマ"
    icon="book-open"
  >}}
  {{< hextra/feature-card
    title="クリエイタースタジオ"
    subtitle="誰でも簡単にコンテンツを作成し、知識を共有できます"
    icon="video-camera"
  >}}
  {{< hextra/feature-card
    title="ソーシャルインタラクション"
    subtitle="いいね、コメント、保存、共有でコミュニティと共に成長しましょう"
    icon="chat"
  >}}
  {{< hextra/feature-card
    title="多言語サポート"
    subtitle="韓国語、英語、日本語で世界中のユーザーと繋がりましょう"
    icon="globe-alt"
  >}}
  {{< hextra/feature-card
    title="アナリティクス"
    subtitle="クリエイターのための詳細な統計とインサイトを提供します"
    icon="chart-bar"
  >}}
{{< /hextra/feature-grid >}}

<div class="hx:mt-16 hx:mb-6">
<h2 class="hx:text-2xl hx:font-bold">なぜUpvyなのか?</h2>
</div>

{{< hextra/feature-grid >}}
  {{< hextra/feature-card
    title="罪悪感のないスクロール"
    subtitle="遊びながら何かを得られる感覚。TikTokのように楽しいけれど、スクロールしているうちに新しいことを学べます。"
    icon="heart"
  >}}
  {{< hextra/feature-card
    title="自然な成長"
    subtitle="勉強している感覚なく自然に学ぶ。堅苦しい教育ではなく、興味深いインサイトを提供します。"
    icon="academic-cap"
  >}}
  {{< hextra/feature-card
    title="日常の中の学習"
    subtitle="通勤時間、休憩時間に気軽に。短時間でも意味のある学習が可能です。"
    icon="clock"
  >}}
  {{< hextra/feature-card
    title="意味のある時間"
    subtitle="「また時間を無駄にした」→「へー、これ知らなかった!」時間を投資するのではなく、時間を活用する体験。"
    icon="fire"
  >}}
{{< /hextra/feature-grid >}}

<div class="hx:mt-16 hx:mb-8 hx:text-center">
<h2 class="hx:text-2xl hx:font-bold hx:mb-6">今すぐ始めましょう</h2>
<div class="hx:flex hx:gap-4 hx:justify-center hx:flex-wrap">
{{< hextra/hero-button text="App Store" link="https://apps.apple.com/jp/app/upvy-%E5%AD%A6%E3%81%B9%E3%82%8B%E3%82%B7%E3%83%A7%E3%83%BC%E3%83%88%E5%8B%95%E7%94%BB/id6756291696" >}}
{{< hextra/hero-button text="Google Play" link="https://play.google.com/store/apps/details?id=com.upvy" >}}
</div>
</div>

<div class="hx:mt-12 hx:mb-8 hx:text-center">
<div class="hx:text-sm hx:text-gray-600">
<a href="/ja/privacy" class="hx:text-primary-600 hover:hx:underline">プライバシーポリシー</a>
&nbsp;|&nbsp;
<a href="/ja/terms" class="hx:text-primary-600 hover:hx:underline">利用規約</a>
&nbsp;|&nbsp;
<a href="/ja/support" class="hx:text-primary-600 hover:hx:underline">サポート</a>
</div>
<p class="hx:text-xs hx:text-gray-500 hx:mt-4">© 2025 Upvy. All rights reserved.</p>
</div>

</main>
