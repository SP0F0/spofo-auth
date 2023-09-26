package net.spofo.auth.service;

import lombok.RequiredArgsConstructor;
import net.spofo.auth.dto.MemberResponse;
import net.spofo.auth.entity.Member;
import net.spofo.auth.exception.NoSocialIdException;
import net.spofo.auth.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
@Service
public class MemberService {

    private final MemberRepository memberRepository;

    private RestClient restClient = RestClient.builder().build();

    public String getStock() {
        return restClient.get()
                .uri("https://www.stock.spofo.net/test/callStock:8080")
                .retrieve()
                .body(String.class);
    }

    public String getPortfolio() {
        return restClient.get()
                .uri("https://www.portfolio.spofo.net/test/callPortfolio:8080")
                .retrieve()
                .body(String.class);
    }

    public MemberResponse findBySocialId(String socialId) {
        Member member = memberRepository.findBySocialId(socialId)
                .orElseThrow(() -> new NoSocialIdException("id를 찾을 수 없습니다."));

        return MemberResponse.from(member);
    }
}