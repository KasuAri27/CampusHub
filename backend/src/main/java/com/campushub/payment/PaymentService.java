package com.campushub.payment;

import com.campushub.common.BusinessException;
import com.campushub.order.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import java.util.Map;

/** 网络调用位于数据库事务之外；重试保持相同商户单号，未知结果只能查单而不能伪造成功。 */
@Service @RequiredArgsConstructor @Slf4j
public class PaymentService {
    private final List<PaymentGateway> gateways;
    private final PaymentAttemptRepository attempts;
    private final PaymentTransactionService transactions;
    private final OrderService orders;

    public List<PaymentAttempt.Channel> availableChannels() {
        return gateways.stream().filter(PaymentGateway::enabled).map(PaymentGateway::channel).toList();
    }

    public PaymentView create(Long orderId, Long actorId, PaymentAttempt.Channel channel) {
        PaymentGateway gateway = gateway(channel);
        // QR码失效后先查旧单。只有渠道明确关闭，才允许创建另一张支付单。
        var previous = attempts.findByOrderId(orderId);
        if (previous.isPresent() && previous.get().getStatus() == PaymentAttempt.Status.PENDING && previous.get().getCodeUrl() != null) {
            orders.getDetail(orderId, actorId);
            reconcile(previous.get());
        }
        PaymentAttempt attempt = transactions.reserve(orderId, actorId, channel);
        if (attempt.getCodeUrl() != null) return view(attempt);
        try {
            String code = gateway.prepay(attempt.getOutTradeNo(), attempt.getAmount(), "CampusHub订单#" + orderId);
            return view(transactions.saveCode(attempt.getId(), code));
        } catch (BusinessException ex) { throw ex; }
        catch (RuntimeException ex) { throw new BusinessException(502, "支付下单结果未知，请查询原支付单后重试，勿重复付款"); }
    }

    public PaymentView query(Long orderId, Long actorId) {
        orders.getDetail(orderId, actorId);
        PaymentAttempt attempt = attempts.findByOrderId(orderId).orElseThrow(() -> new BusinessException(404, "尚未创建支付单"));
        if (attempt.getStatus() != PaymentAttempt.Status.PAID) reconcile(attempt);
        return view(attempts.findById(attempt.getId()).orElseThrow());
    }

    public void notify(PaymentAttempt.Channel channel, String rawBody, Map<String, String> parameters) {
        PaymentGateway.VerifiedPayment result;
        try { result = gateway(channel).verifyNotification(rawBody, parameters); }
        catch (BusinessException ex) { throw ex; }
        catch (RuntimeException ex) { throw new BusinessException(400, "支付通知签名或格式不正确"); }
        if (result.channel() != channel) throw new BusinessException(400, "支付渠道不匹配");
        transactions.recordVerified(result);
    }

    private void reconcile(PaymentAttempt attempt) {
        transactions.markQueried(attempt.getId());
        var result = gateway(attempt.getChannel()).query(attempt.getOutTradeNo());
        if (!attempt.getOutTradeNo().equals(result.outTradeNo()) || result.channel() != attempt.getChannel())
            throw new BusinessException(400, "查单响应与支付单不匹配");
        transactions.recordVerified(result);
    }

    /** 小批量公平轮询，补偿丢失回调；失败只记录单号和异常类型，不输出密钥或渠道原报文。 */
    @Scheduled(fixedDelay = 60000)
    public void reconcilePending() {
        for (var attempt : attempts.findByStatusOrderByLastQueriedAtAscIdAsc(PaymentAttempt.Status.PENDING, PageRequest.of(0, 10))) {
            try { reconcile(attempt); }
            catch (RuntimeException ex) { log.warn("支付查单待重试，attemptId={}，type={}", attempt.getId(), ex.getClass().getSimpleName()); }
        }
    }

    private PaymentGateway gateway(PaymentAttempt.Channel channel) {
        return gateways.stream().filter(g -> g.channel() == channel && g.enabled()).findFirst()
                .orElseThrow(() -> new BusinessException(503, "该支付渠道尚未配置或未启用"));
    }
    private PaymentView view(PaymentAttempt p) {
        return new PaymentView(p.getOrderId(), p.getOutTradeNo(), p.getChannel(), p.getAmount(), p.getCodeUrl(), p.getStatus());
    }
    public record PaymentView(Long orderId, String outTradeNo, PaymentAttempt.Channel channel,
                              java.math.BigDecimal amount, String codeUrl, PaymentAttempt.Status status) { }
}
