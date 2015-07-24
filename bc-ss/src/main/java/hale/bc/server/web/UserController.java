package hale.bc.server.web;

import hale.bc.server.repository.UserDao;
import hale.bc.server.repository.exception.DuplicatedEntryException;
import hale.bc.server.repository.exception.OldPwdErrorException;
import hale.bc.server.repository.exception.ResetPwdLinkErrorException;
import hale.bc.server.service.UserService;
import hale.bc.server.to.FailedResult;
import hale.bc.server.to.User;
import hale.bc.server.to.UserStatus;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.List;

import javax.mail.MessagingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.query.Param;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/users")
public class UserController {
	
	@Autowired
	private UserDao userDao;
	
	@Autowired
	private UserService userService;
	
	@Autowired
	private PasswordEncoder passwordEncoder;
	
	@RequestMapping(method=RequestMethod.POST)
    public User add(@RequestBody User user) throws DuplicatedEntryException, MessagingException {
		user.setStatus(UserStatus.New);
		user.getRoles().add("USER");
		user.setPassword(passwordEncoder.encode(user.getPassword()));
		return userService.createUser(user);
    }
	
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	@RequestMapping(value = "/{uid}", method=RequestMethod.GET, params="!by")
    public User get(@PathVariable Long uid)  {
		return userDao.getUserById(uid);
    }
	
	@PreAuthorize("(hasRole('ROLE_USER') and #n == authentication.name) or hasRole('ROLE_ADMIN')")
	@RequestMapping(value = "/{name:.+}", method=RequestMethod.GET, params="by=name")
    public User getByName(@Param("n") @PathVariable String name)  {
		return userDao.getUserByName(name);
    }
	
	@RequestMapping(value = "/auth", method=RequestMethod.GET)
    public User getAuth(Principal principal)  {
		return userDao.getUserByName(principal.getName());
    }
	
	@RequestMapping(value = "/active", method=RequestMethod.PUT, params="code")
    public User activeByCode(@RequestParam(value = "code", required = true) String code)  {
		return userDao.activeUser(code);
    }
	
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	@RequestMapping(method=RequestMethod.GET)
    public List<User> getAll()  {
		return userDao.getAllUsers();
    }
	
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	@RequestMapping(value = "/{uid}/active", method=RequestMethod.PUT)
    public User active(@PathVariable Long uid)  {
		return userDao.updateUserStatus(uid, UserStatus.Active);
    }
	
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	@RequestMapping(value = "/{uid}/inactive", method=RequestMethod.PUT)
    public User inactive(@PathVariable Long uid)  {
		return userDao.updateUserStatus(uid, UserStatus.Inactive);
    }
	
	@RequestMapping(value = "/{uid}/editPwd", method=RequestMethod.PUT)
    public User eidtPassword(@RequestBody User user, @PathVariable Long uid, Principal principal)  throws DuplicatedEntryException, OldPwdErrorException {
		if (!user.getName().equals(principal.getName())) {
			return null;
		}
		return userDao.eidtPassword(user);
    }
	
	@RequestMapping(value = "/{uid}/forgetPwd", method=RequestMethod.PUT)
    public User forgetPwd(@RequestBody User user, @PathVariable Long uid)  throws DuplicatedEntryException, MessagingException {
		return userService.forgetPwd(user.getName());
    }
	
	@RequestMapping(value = "/{uid}/resetPwd", method=RequestMethod.PUT)
    public User resetPwd(@RequestBody User user, @PathVariable Long uid)  throws DuplicatedEntryException, ResetPwdLinkErrorException {
		return userDao.resetPwd(user);
    }
	
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	@RequestMapping(value = "/{uid}/resendCode", method=RequestMethod.PUT)
    public User resendCode(@RequestBody User user, @PathVariable Long uid)  throws DuplicatedEntryException, MessagingException {
		return userService.resendCode(user);
    }
	
	@ExceptionHandler(DuplicatedEntryException.class)
	public FailedResult handleCustomException(DuplicatedEntryException ex) {
		return new FailedResult(-1, ex.getMessage());
	}
	
	@ExceptionHandler(OldPwdErrorException.class)
	public FailedResult handleCustomException(OldPwdErrorException ex) {
		return new FailedResult(-11, ex.getMessage());
	}
	
	@ExceptionHandler(FileNotFoundException.class)
	public FailedResult handleCustomException(FileNotFoundException ex) {
		return new FailedResult(-12, "Config file not found.");
	}
	
	@ExceptionHandler(MessagingException.class)
	public FailedResult handleCustomException(MessagingException ex) {
		return new FailedResult(-13, "Mail send fail.");
	}
	
	@ExceptionHandler(ResetPwdLinkErrorException.class)
	public FailedResult handleCustomException(ResetPwdLinkErrorException ex) {
		return new FailedResult(-14, ex.getMessage());
	}
	
}
