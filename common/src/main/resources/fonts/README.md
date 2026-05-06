# 지시서 PDF용 한글 폰트

이 디렉터리에는 지시서 PDF 렌더링에 쓰는 한글 폰트 TTF 파일이 들어갑니다.

## 필요 파일

- `NanumGothic-Regular.ttf`
- `NanumGothic-Bold.ttf` (선택, 헤더용)

## 다운로드

1. 네이버 한글한글 아름답게 페이지: https://hangeul.naver.com/font
2. 또는 GitHub `naver/nanumfont` 리포지터리
3. 받은 파일을 이 폴더에 그대로 넣으면 됩니다 (`.ttf` 확장자 그대로).

## 라이선스

NanumGothic은 SIL Open Font License 1.1 (OFL) 적용. 상용 사용·재배포·임베드 모두 허용됩니다.
폰트와 함께 받은 LICENSE 또는 OFL.txt를 같은 폴더에 동봉해 주세요.

## 동작 방식

- Renderer(stock 모듈)가 `ITextFontResolver.addFont("classpath:/fonts/NanumGothic-Regular.ttf", ...)`로 등록
- Thymeleaf 템플릿 CSS에서 `font-family: 'NanumGothic'` 사용
- Flying Saucer가 PDF에 폰트를 임베드하므로 PDF 파일 자체에서 폰트가 누락될 일은 없음

## 폰트 파일이 없으면

- 한글이 모두 □(豆腐) 또는 빈칸으로 렌더됩니다.
- 빌드는 통과하지만 통합 테스트(Step 10)에서 한글 육안 확인 단계에서 실패합니다.
