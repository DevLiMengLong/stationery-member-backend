package com.gechuang.stationery.mainflow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MainFlowMapper {

    Map<String, Object> selectStoreByAccount(@Param("account") String account);

    Long lockStoreLoginMobile(@Param("mobile") String mobile);

    List<Map<String, Object>> selectStoresByLoginMobile(@Param("mobile") String mobile,
                                                        @Param("now") LocalDateTime now);

    List<Map<String, Object>> selectStoresByLoginMobileAndPassword(@Param("mobile") String mobile,
                                                                   @Param("passwordHash") String passwordHash,
                                                                   @Param("now") LocalDateTime now);

    Map<String, Object> selectStoreByLoginMobile(@Param("mobile") String mobile,
                                                 @Param("storeId") Long storeId,
                                                 @Param("now") LocalDateTime now);

    List<String> selectStoreLoginMobiles(@Param("storeId") Long storeId);

    int deleteStoreLoginMobiles(@Param("storeId") Long storeId);

    int insertStoreLoginMobiles(@Param("storeId") Long storeId, @Param("mobiles") List<String> mobiles);

    int countRecentVerificationCodes(@Param("mobile") String mobile,
                                     @Param("scene") String scene,
                                     @Param("since") LocalDateTime since);

    int supersedeUnusedVerificationCodes(@Param("mobile") String mobile, @Param("scene") String scene);

    int insertVerificationCode(@Param("mobile") String mobile,
                              @Param("scene") String scene,
                              @Param("code") String code,
                              @Param("expiresAt") LocalDateTime expiresAt);

    Map<String, Object> selectLatestVerificationCodeForUpdate(@Param("mobile") String mobile,
                                                              @Param("scene") String scene);

    int incrementVerificationCodeAttempts(@Param("id") Long id);

    int markVerificationCodeUsed(@Param("id") Long id);

    int markVerificationCodeExpired(@Param("id") Long id);

    int lockVerificationCode(@Param("id") Long id);

    int insertMerchantLoginChallenge(@Param("token") String token,
                                     @Param("mobile") String mobile,
                                     @Param("expiresAt") LocalDateTime expiresAt);

    int insertMerchantPasswordLoginChallenge(@Param("token") String token,
                                             @Param("mobile") String mobile,
                                             @Param("expiresAt") LocalDateTime expiresAt);

    Map<String, Object> selectMerchantLoginChallengeForUpdate(@Param("token") String token,
                                                              @Param("now") LocalDateTime now);

    int deleteMerchantLoginChallenge(@Param("id") Long id);

    int deleteExpiredMerchantLoginChallenges(@Param("now") LocalDateTime now);

    Map<String, Object> selectStoreById(@Param("id") Long id);

    Map<String, Object> selectAdminByUsername(@Param("username") String username);

    Map<String, Object> selectAdminById(@Param("id") Long id);

    Map<String, Object> selectStoreByToken(@Param("token") String token, @Param("now") LocalDateTime now);

    Map<String, Object> selectStoreByWechatOpenid(@Param("openid") String openid);

    List<Map<String, Object>> selectStoreWechatBindings(@Param("storeId") Long storeId);

    Map<String, Object> selectStoreWechatPayConfig(@Param("storeId") Long storeId);

    Map<String, Object> selectAdminByToken(@Param("token") String token, @Param("now") LocalDateTime now);

    int insertSession(@Param("token") String token,
                      @Param("principalType") String principalType,
                      @Param("principalId") Long principalId,
                      @Param("expiresAt") LocalDateTime expiresAt);

    int insertWechatSession(@Param("token") String token,
                            @Param("principalId") Long principalId,
                            @Param("bindingId") Long bindingId,
                            @Param("expiresAt") LocalDateTime expiresAt);

    int insertStoreWechatBinding(@Param("storeId") Long storeId, @Param("openid") String openid);

    int deleteStoreWechatBinding(@Param("storeId") Long storeId, @Param("bindingId") Long bindingId);

    int deleteSession(@Param("token") String token);

    int updateStoreLastLogin(@Param("id") Long id);

    int updateAdminLastLogin(@Param("id") Long id);

    Map<String, Object> selectAdminOverview();

    List<Map<String, Object>> selectOverviewStores();

    Map<String, Object> selectMerchantDashboard(@Param("storeId") Long storeId,
                                                @Param("todayStart") LocalDateTime todayStart,
                                                @Param("tomorrowStart") LocalDateTime tomorrowStart,
                                                @Param("weekStart") LocalDateTime weekStart,
                                                @Param("monthStart") LocalDateTime monthStart);

    List<Map<String, Object>> selectRechargeTiers(@Param("storeId") Long storeId);

    Map<String, Object> selectRechargeTier(@Param("storeId") Long storeId, @Param("tierNo") int tierNo);

    int updateRechargeTier(@Param("storeId") Long storeId,
                           @Param("tierNo") int tierNo,
                           @Param("rechargeAmount") BigDecimal rechargeAmount,
                           @Param("giftAmount") BigDecimal giftAmount);

    int countStores(@Param("keyword") String keyword, @Param("status") String status);

    List<Map<String, Object>> selectStores(@Param("keyword") String keyword,
                                           @Param("status") String status,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    int insertStore(Map<String, Object> store);

    int updateStore(Map<String, Object> store);

    int updateStoreSmsQuota(@Param("id") Long id, @Param("smsQuota") int smsQuota);

    int tryConsumeSmsQuota(@Param("storeId") Long storeId);

    int restoreSmsQuota(@Param("storeId") Long storeId);

    int updateStoreStatus(@Param("id") Long id, @Param("status") String status);

    int updateStorePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    int insertDefaultRechargeTiers(@Param("storeId") Long storeId);

    int countMerchantMembers(@Param("storeId") Long storeId, @Param("keyword") String keyword);

    List<Map<String, Object>> selectMerchantMembers(@Param("storeId") Long storeId,
                                                    @Param("keyword") String keyword,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit);

    int countAdminMembers(@Param("mobile") String mobile,
                          @Param("name") String name,
                          @Param("storeId") Long storeId,
                          @Param("rechargeMin") BigDecimal rechargeMin,
                          @Param("rechargeMax") BigDecimal rechargeMax,
                          @Param("consumptionMin") BigDecimal consumptionMin,
                          @Param("consumptionMax") BigDecimal consumptionMax);

    List<Map<String, Object>> selectAdminMembers(@Param("mobile") String mobile,
                                                 @Param("name") String name,
                                                 @Param("storeId") Long storeId,
                                                 @Param("rechargeMin") BigDecimal rechargeMin,
                                                 @Param("rechargeMax") BigDecimal rechargeMax,
                                                 @Param("consumptionMin") BigDecimal consumptionMin,
                                                 @Param("consumptionMax") BigDecimal consumptionMax,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    List<Map<String, Object>> selectAdminMembersForExport(@Param("mobile") String mobile,
                                                          @Param("name") String name,
                                                          @Param("storeId") Long storeId,
                                                          @Param("rechargeMin") BigDecimal rechargeMin,
                                                          @Param("rechargeMax") BigDecimal rechargeMax,
                                                          @Param("consumptionMin") BigDecimal consumptionMin,
                                                          @Param("consumptionMax") BigDecimal consumptionMax,
                                                          @Param("limit") int limit);

    Map<String, Object> selectMemberDetail(@Param("id") Long id, @Param("storeId") Long storeId);

    Map<String, Object> selectMemberDetailForUpdate(@Param("id") Long id, @Param("storeId") Long storeId);

    List<Map<String, Object>> selectMembersByMobileKeyword(@Param("storeId") Long storeId,
                                                           @Param("keyword") String keyword);

    Map<String, Object> selectMemberByMobile(@Param("storeId") Long storeId, @Param("mobile") String mobile);

    List<String> selectVisibleMemberMobiles(@Param("storeId") Long storeId,
                                            @Param("mobiles") List<String> mobiles);

    int insertImportedMembers(@Param("members") List<Map<String, Object>> members);

    List<Map<String, Object>> selectMemberIdsByMobiles(@Param("storeId") Long storeId,
                                                        @Param("mobiles") List<String> mobiles);

    int insertWallets(@Param("wallets") List<Map<String, Object>> wallets);

    int insertMember(Map<String, Object> member);

    int insertWallet(@Param("memberId") Long memberId);

    int updateMember(Map<String, Object> member);

    int softDeleteMember(@Param("id") Long id, @Param("storeId") Long storeId);

    int countTransactions(@Param("memberId") Long memberId,
                          @Param("storeId") Long storeId,
                          @Param("type") String type);

    List<Map<String, Object>> selectTransactions(@Param("memberId") Long memberId,
                                                 @Param("storeId") Long storeId,
                                                 @Param("type") String type,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    long countAdminTransactions(@Param("type") String type,
                                @Param("mobile") String mobile,
                                @Param("name") String name,
                                @Param("storeId") Long storeId,
                                @Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime,
                                @Param("amountMin") BigDecimal amountMin,
                                @Param("amountMax") BigDecimal amountMax);

    List<Map<String, Object>> selectAdminTransactions(@Param("type") String type,
                                                       @Param("mobile") String mobile,
                                                       @Param("name") String name,
                                                       @Param("storeId") Long storeId,
                                                       @Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime,
                                                       @Param("amountMin") BigDecimal amountMin,
                                                       @Param("amountMax") BigDecimal amountMax,
                                                       @Param("offset") int offset,
                                                       @Param("limit") int limit);

    List<Map<String, Object>> selectRecentTransactions(@Param("storeId") Long storeId,
                                                       @Param("type") String type,
                                                       @Param("limit") int limit);

    Map<String, Object> selectWalletForUpdate(@Param("memberId") Long memberId);

    int updateWallet(Map<String, Object> wallet);

    int insertTransaction(Map<String, Object> transaction);

    Map<String, Object> selectSuccessfulTransactionByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    Map<String, Object> selectTransactionForUpdate(@Param("id") Long id, @Param("storeId") Long storeId);

    int insertWechatPaymentOrder(Map<String, Object> order);

    Map<String, Object> selectWechatPaymentOrderByStore(@Param("outTradeNo") String outTradeNo,
                                                         @Param("storeId") Long storeId);

    Map<String, Object> selectWechatPaymentOrderByIdempotencyKey(@Param("storeId") Long storeId,
                                                                  @Param("idempotencyKey") String idempotencyKey);

    Map<String, Object> selectWechatPaymentOrderForUpdate(@Param("outTradeNo") String outTradeNo);

    int updateWechatPaymentOrderPrepay(@Param("outTradeNo") String outTradeNo,
                                       @Param("codeUrl") String codeUrl,
                                       @Param("status") String status);

    int claimWechatPaymentOrderQuery(@Param("outTradeNo") String outTradeNo,
                                     @Param("storeId") Long storeId,
                                     @Param("now") LocalDateTime now,
                                     @Param("queryBefore") LocalDateTime queryBefore);

    int updateWechatPaymentOrderTerminal(@Param("outTradeNo") String outTradeNo,
                                         @Param("status") String status,
                                         @Param("wechatTransactionId") String wechatTransactionId,
                                         @Param("failureCode") String failureCode,
                                         @Param("failureMessage") String failureMessage,
                                         @Param("paidAt") LocalDateTime paidAt);

    int linkWechatPaymentOrderTransaction(@Param("outTradeNo") String outTradeNo,
                                          @Param("walletTransactionId") Long walletTransactionId);

    Map<String, Object> selectWechatPaymentOrderByWalletTransaction(
            @Param("walletTransactionId") Long walletTransactionId);

    List<Map<String, Object>> selectExpiredWechatPaymentOrders(@Param("now") LocalDateTime now,
                                                                @Param("limit") int limit);

    int claimWechatPaymentOrderForClose(@Param("id") Long id);

    int markTransactionReversed(@Param("id") Long id);

    int updateMerchantProfile(@Param("id") Long id,
                              @Param("shopName") String shopName,
                              @Param("avatarUrl") String avatarUrl);

    List<Map<String, Object>> selectActivityRows();
}
