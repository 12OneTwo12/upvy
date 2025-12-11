---
title: Upvy
layout: hextra-home
---

<main class="home-wrapper">

<div class="hero-grid">
<div class="hero-content">

{{< hextra/hero-badge >}}
  <div class="hx:w-2 hx:h-2 hx:rounded-full hx:bg-primary-400"></div>
  <span>Beta 출시 준비중</span>
{{< /hextra/hero-badge >}}

<div class="hx:mt-8 hx:mb-2">
{{< hextra/hero-headline >}}
  스크롤 시간을&nbsp;<br class="hx:sm:block hx:hidden" />성장 시간으로
{{< /hextra/hero-headline >}}
</div>

<div class="hx:mb-8">
{{< hextra/hero-subtitle >}}
  재미있게 스크롤하며 자연스럽게 배우는 숏폼 학습 플랫폼.<br />
  부담스러운 학습이 아닌, 자연스러운 성장을 경험하세요.
{{< /hextra/hero-subtitle >}}
</div>

<div class="button-container">
{{< hextra/hero-button text="App Store에서 다운로드" link="https://apps.apple.com/app/upvy" >}}
{{< hextra/hero-button text="Google Play에서 다운로드" link="https://play.google.com/store/apps/details?id=com.upvy" >}}
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
<h2 class="hx:text-2xl hx:font-bold">핵심 기능</h2>
</div>

{{< hextra/feature-grid >}}
  {{< hextra/feature-card
    title="스마트 피드"
    subtitle="AI 기반 추천 알고리즘이 당신의 관심사에 맞는 콘텐츠를 제공합니다"
    icon="sparkles"
  >}}
  {{< hextra/feature-card
    title="다양한 카테고리"
    subtitle="프로그래밍, 디자인, 언어, 비즈니스, 동기부여 등 다양한 주제"
    icon="book-open"
  >}}
  {{< hextra/feature-card
    title="크리에이터 스튜디오"
    subtitle="누구나 쉽게 콘텐츠를 만들고 지식을 공유할 수 있습니다"
    icon="video-camera"
  >}}
  {{< hextra/feature-card
    title="소셜 인터랙션"
    subtitle="좋아요, 댓글, 저장, 공유로 커뮤니티와 함께 성장하세요"
    icon="chat"
  >}}
  {{< hextra/feature-card
    title="다국어 지원"
    subtitle="한국어, 영어, 일본어로 전세계 사용자들과 연결되세요"
    icon="globe-alt"
  >}}
  {{< hextra/feature-card
    title="애널리틱스"
    subtitle="크리에이터를 위한 상세한 통계와 인사이트를 제공합니다"
    icon="chart-bar"
  >}}
{{< /hextra/feature-grid >}}

<div class="hx:mt-16 hx:mb-6">
<h2 class="hx:text-2xl hx:font-bold">왜 Upvy인가요?</h2>
</div>

{{< hextra/feature-grid >}}
  {{< hextra/feature-card
    title="죄책감 없는 스크롤"
    subtitle="놀면서도 뭔가 얻어가는 기분. TikTok처럼 재미있지만, 스크롤하다 보면 어느새 새로운 것을 배우게 됩니다."
    icon="heart"
  >}}
  {{< hextra/feature-card
    title="자연스러운 성장"
    subtitle="공부한다는 느낌 없이 자연스레 배우기. 딱딱한 교육이 아닌, 흥미로운 인사이트를 제공합니다."
    icon="academic-cap"
  >}}
  {{< hextra/feature-card
    title="일상 속 학습"
    subtitle="출퇴근, 쉬는 시간에 부담없이. 짧은 시간에도 의미있는 학습이 가능합니다."
    icon="clock"
  >}}
  {{< hextra/feature-card
    title="의미있는 시간"
    subtitle="\"또 시간 낭비했다\" → \"오, 이거 몰랐는데!\". 시간을 투자하는 것이 아닌, 시간을 활용하는 경험."
    icon="fire"
  >}}
{{< /hextra/feature-grid >}}

<div class="hx:mt-16 hx:mb-8 hx:text-center">
<h2 class="hx:text-2xl hx:font-bold hx:mb-6">지금 시작하세요</h2>
<div class="hx:flex hx:gap-4 hx:justify-center hx:flex-wrap">
{{< hextra/hero-button text="App Store" link="https://apps.apple.com/app/upvy" >}}
{{< hextra/hero-button text="Google Play" link="https://play.google.com/store/apps/details?id=com.upvy" >}}
</div>
</div>

<div class="hx:mt-12 hx:mb-8 hx:text-center">
<div class="hx:text-sm hx:text-gray-600">
<a href="/privacy" class="hx:text-primary-600 hover:hx:underline">개인정보처리방침</a>
&nbsp;|&nbsp;
<a href="/terms" class="hx:text-primary-600 hover:hx:underline">이용약관</a>
&nbsp;|&nbsp;
<a href="/support" class="hx:text-primary-600 hover:hx:underline">고객 지원</a>
</div>
<p class="hx:text-xs hx:text-gray-500 hx:mt-4">© 2025 Upvy. All rights reserved.</p>
</div>

</main>
