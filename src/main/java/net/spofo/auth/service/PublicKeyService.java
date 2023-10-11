package net.spofo.auth.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import net.spofo.auth.dto.response.MemberResponse;
import net.spofo.auth.entity.PublicKey;
import net.spofo.auth.repository.PublicKeyRepository;
import net.spofo.auth.exception.ExpiredTokenException;
import net.spofo.auth.exception.InvalidTokenException;
import net.spofo.auth.exception.InvalidJSONException;

@RequiredArgsConstructor
@Service
public class PublicKeyService {

    private final MemberService memberService;
    private final PublicKeyRepository publicKeyRepository;
    private final RestClient restClient;
    private final String issuer = "https://kauth.kakao.com";
    private final String KAKAO_PUBLIC_KEY_URL = "https://kauth.kakao.com/.well-known/jwks.json";

    @Value("${auth.kakao.clientid}")
    private String appKey;

    public MemberResponse verifyToken(String token) { // 토큰 검증
        DecodedJWT jwtOrigin = verifyValidation(token);

        if (verifySignature(jwtOrigin) == false) { // 토큰의 공개키가 유효하지 않다면 DB를 업데이트하거나 실패라고 알려주거나
            getKakaoPublicKeys(token); // 예외 발생 없이 잘 돌아오면 정상적인 토큰. (db가 업데이트 된 상태이므로 한 번 더 서명 검증 필요)
            if (verifySignature(jwtOrigin) == false) {
                throw InvalidTokenException.EXCEPTION;
            }
        }
        // 토큰 검증 완료!
        String socialId = jwtOrigin.getSubject();
        MemberResponse memberResponse = memberService.findBySocialId(socialId);
        return memberResponse;
    }

    public boolean verifySignature(DecodedJWT jwtOrigin) { // 토큰의 공개키와 비교하여 서명 검증
        List<PublicKey> storedPublicKey = loadPublicKey();

        for (int i = 0; i < storedPublicKey.size(); i++) {
            if (jwtOrigin.getKeyId().equals(storedPublicKey.get(i).getPublickey())) {
                return true; // 토큰의 공개키가 유효함.
            }
        }
        return false;
    }

    public void getKakaoPublicKeys(String token) {
        // 카카오 공개키 목록 가져오기
        ResponseEntity response = restClient.get()
                .uri(KAKAO_PUBLIC_KEY_URL)
                .retrieve()
                .toEntity(String.class);

        String kidJson = response.getBody().toString();
        List<String> publicKeyList = new ArrayList<>();
        List<PublicKey> storedPublicKeyList = loadPublicKey();

        try {
            // 1. 데이터 파싱
            JSONObject jsonObject = new JSONObject(kidJson);
            JSONArray keysArray = jsonObject.getJSONArray("keys");

            // 2. 파싱한 데이터로 리스트 만들기
            for (int i = 0; i < keysArray.length(); i++) {
                JSONObject keyObject = keysArray.getJSONObject(i);
                String kid = keyObject.getString("kid");
                publicKeyList.add(kid);
            }
        } catch (Exception e) { //JSONExecption
            throw InvalidJSONException.EXCEPTION;
        }
        if (!matchPublicKey(publicKeyList,
                storedPublicKeyList)) { // 만약 불러온 pk와 저장된 pk가 다르다면 공개키가 업데이트 된 것이므로 DB 업데이트
            saveNewPublicKey(publicKeyList);
        }
    }

    public boolean matchPublicKey(List<String> publicKeyList, List<PublicKey> storedPublicKeyList) {
        for (int i = 0; i < publicKeyList.size(); i++) {
            for (int j = 0; j < storedPublicKeyList.size(); j++) {
                if (publicKeyList.get(i).equals(storedPublicKeyList.get(j).getPublickey())) {
                    throw InvalidTokenException.EXCEPTION;
                }
            }
        }
        return false;
    }

    public void saveNewPublicKey(List<String> publicKeyList) {
        deleteAllPublicKey();
        publicKeyList.stream()
                .map(PublicKey::new) // 각 요소를 PublicKey 객체로 변환
                .forEach(this::savePublicKey); // 각 PublicKey를 저장
    }

    public DecodedJWT verifyValidation(String token) {
        DecodedJWT jwtOrigin = JWT.decode(token);

        if (!jwtOrigin.getIssuer().equals(issuer)
                || !jwtOrigin.getAudience().get(0).equals(appKey)) {
            throw InvalidTokenException.EXCEPTION;
        }
        if (jwtOrigin.getExpiresAt().before(new Date(System.currentTimeMillis()))) {
            throw ExpiredTokenException.EXCEPTION;
        }
        return jwtOrigin;
    }

    public PublicKey savePublicKey(PublicKey publicKey) {
        return publicKeyRepository.save(publicKey);
    }

    public void deleteAllPublicKey() {
        publicKeyRepository.deleteAllInBatch();
    }

    public List<PublicKey> loadPublicKey() {
        return publicKeyRepository.findAll();
    }
}