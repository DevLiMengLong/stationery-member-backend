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

    Map<String, Object> selectStoreById(@Param("id") Long id);

    Map<String, Object> selectAdminByUsername(@Param("username") String username);

    Map<String, Object> selectAdminById(@Param("id") Long id);

    Map<String, Object> selectStoreByToken(@Param("token") String token, @Param("now") LocalDateTime now);

    Map<String, Object> selectAdminByToken(@Param("token") String token, @Param("now") LocalDateTime now);

    int insertSession(@Param("token") String token,
                      @Param("principalType") String principalType,
                      @Param("principalId") Long principalId,
                      @Param("expiresAt") LocalDateTime expiresAt);

    int deleteSession(@Param("token") String token);

    int updateStoreLastLogin(@Param("id") Long id);

    int updateAdminLastLogin(@Param("id") Long id);

    Map<String, Object> selectAdminOverview();

    List<Map<String, Object>> selectOverviewStores();

    Map<String, Object> selectMerchantDashboard(@Param("storeId") Long storeId,
                                                @Param("todayStart") LocalDateTime todayStart,
                                                @Param("tomorrowStart") LocalDateTime tomorrowStart,
                                                @Param("weekStart") LocalDateTime weekStart);

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

    List<Map<String, Object>> selectRecentTransactions(@Param("storeId") Long storeId,
                                                       @Param("type") String type,
                                                       @Param("limit") int limit);

    Map<String, Object> selectWalletForUpdate(@Param("memberId") Long memberId);

    int updateWallet(Map<String, Object> wallet);

    int insertTransaction(Map<String, Object> transaction);

    Map<String, Object> selectTransactionForUpdate(@Param("id") Long id, @Param("storeId") Long storeId);

    int markTransactionReversed(@Param("id") Long id);

    int updateMerchantProfile(@Param("id") Long id,
                              @Param("shopName") String shopName,
                              @Param("avatarUrl") String avatarUrl);

    List<Map<String, Object>> selectActivityRows();
}
