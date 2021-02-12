package top.hcode.hoj.controller;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.*;
import top.hcode.hoj.common.CommonResult;
import top.hcode.hoj.common.exception.CompileError;
import top.hcode.hoj.common.exception.SystemError;
import top.hcode.hoj.pojo.entity.*;
import top.hcode.hoj.remoteJudge.submit.RemoteJudgeSubmitDispatcher;
import top.hcode.hoj.service.impl.*;
import top.hcode.hoj.util.Constants;
import top.hcode.hoj.util.IpUtils;


/**
 * @Author: Himit_ZH
 * @Date: 2020/10/29 22:22
 * @Description: 处理代码提交
 */
@RestController
@Slf4j
@RefreshScope
public class JudgeController {

    @Autowired
    private JudgeServiceImpl judgeService;

    @Autowired
    private ProblemServiceImpl problemService;


    @Autowired
    private SystemConfigServiceImpl systemConfigService;

    @Autowired
    private RemoteJudgeSubmitDispatcher remoteJudgeSubmitDispatcher;

    @Value("${hoj.judge.token}")
    private String judgeToken;

    @Value("${hoj-judger.ip}")
    private String ip;

    @PostMapping(value = "/judge")
    public CommonResult submitProblemJudge(@RequestBody ToJudge toJudge) {
        if (!toJudge.getToken().equals(judgeToken)) {
            return CommonResult.errorResponse("对不起！您使用的判题服务调用凭证不正确！访问受限！", CommonResult.STATUS_ACCESS_DENIED);
        }

        Judge judge = toJudge.getJudge();
        if (judge == null || judge.getSubmitId() == null || judge.getUid() == null || judge.getPid() == null) {
            return CommonResult.errorResponse("参数错误！");
        }

        // 当前判题任务数+1
        systemConfigService.updateJudgeTaskNum(true);

        judge.setStatus(Constants.Judge.STATUS_COMPILING.getStatus()); // 标志该判题过程进入编译阶段
        // 获取当前判题系统所在ip写入数据库
        if (ip.equals("-1")) {
            judge.setJudger(IpUtils.getLocalIpv4Address());
        } else {
            judge.setJudger(ip);
        }
        boolean updateResult = judgeService.saveOrUpdate(judge);
        if (!updateResult) { // 出错并不影响主要业务逻辑，可以稍微记录一下即可。
            log.error("修改Judge表失效--------->{}", "修改提交评判为编译中出错");
        }
        // 进行判题操作
        Problem problem = problemService.getById(judge.getPid());

        Judge finalJudge = judgeService.Judge(problem, judge);

        // 更新该次提交
        judgeService.updateById(finalJudge);

        // 更新其它表
        judgeService.updateOtherTable(finalJudge.getSubmitId(),
                finalJudge.getStatus(),
                finalJudge.getCid(),
                finalJudge.getUid(),
                finalJudge.getPid(),
                finalJudge.getScore());

        // 当前判题任务数-1
        systemConfigService.updateJudgeTaskNum(false);
        return CommonResult.successResponse(finalJudge, "判题机评测完成！");
    }


    @PostMapping(value = "/compile-spj")
    public CommonResult compileSpj(@RequestBody CompileSpj compileSpj) {

        if (!compileSpj.getToken().equals(judgeToken)) {
            return CommonResult.errorResponse("对不起！您使用的判题服务调用凭证不正确！访问受限！", CommonResult.STATUS_ACCESS_DENIED);
        }

        try {
            judgeService.compileSpj(compileSpj.getSpjSrc(), compileSpj.getPid(), compileSpj.getSpjLanguage());
            return CommonResult.successResponse(null, "编译成功！");
        } catch (CompileError compileError) {
            return CommonResult.errorResponse(compileError.getStderr(), CommonResult.STATUS_FAIL);
        } catch (SystemError systemError) {
            return CommonResult.errorResponse(systemError.getMessage(), CommonResult.STATUS_ERROR);
        }
    }

    @PostMapping(value = "/remote-judge")
    public CommonResult remoteJudge(@RequestBody ToJudge toJudge) {

        if (!toJudge.getToken().equals(judgeToken)) {
            return CommonResult.errorResponse("对不起！您使用的判题服务调用凭证不正确！访问受限！", CommonResult.STATUS_ACCESS_DENIED);
        }

        Long submitId = toJudge.getJudge().getSubmitId();
        String uid = toJudge.getJudge().getUid();
        Long cid = toJudge.getJudge().getCid();
        Long pid = toJudge.getJudge().getPid();
        // 发送消息
        try {
            String[] source = toJudge.getRemoteJudge().split("-");
            Long remotePid = Long.valueOf(source[1]);
            String remoteJudge = source[0];
            String userCode = toJudge.getJudge().getCode();
            String language = toJudge.getJudge().getLanguage();
            remoteJudgeSubmitDispatcher.sendTask(remoteJudge, remotePid, submitId, uid, cid, pid, language, userCode);
            return CommonResult.successResponse(null, "提交成功");
        } catch (Exception e) {
            // TODO 如果为了测试是否可行，请把数据库更新注释掉，若正常运行，则需要保证数据一致性
            Judge judge = new Judge();
            judge.setSubmitId(submitId)
                    .setStatus(Constants.Judge.STATUS_SYSTEM_ERROR.getStatus())
                    .setErrorMessage("Oops, something has gone wrong with the judgeServer. Please report this to administrator.");
            judgeService.updateById(judge);
            // 更新其它表
            judgeService.updateOtherTable(submitId,
                    Constants.Judge.STATUS_SYSTEM_ERROR.getStatus(),
                    cid,
                    uid,
                    pid,
                    null);
            log.error("调用redis消息发布异常,此次远程判题任务判为系统错误--------------->{}", e.getMessage());
            return CommonResult.errorResponse(e.getMessage(), CommonResult.STATUS_ERROR);
        }
    }
}